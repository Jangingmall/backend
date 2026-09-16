package com.jangingmall.backend.image.application;

import com.jangingmall.backend.global.exception.BusinessRuleViolationException;
import com.jangingmall.backend.global.exception.DomainException;
import com.jangingmall.backend.global.exception.ErrorCode;
import com.jangingmall.backend.image.domain.ImagePurpose;
import com.jangingmall.backend.image.domain.ImageUpload;
import com.jangingmall.backend.image.domain.ImageUploadRepository;
import com.jangingmall.backend.member.application.MemberAccess;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class ImageService {

    private static final String WEBP = "image/webp";
    private static final List<String> PUBLIC_VARIANTS = List.of("320w", "640w", "1280w");
    private static final Set<String> REQUIRED_PUBLIC_VARIANTS = Set.copyOf(PUBLIC_VARIANTS);
    private static final Set<String> REQUIRED_RETURN_VARIANTS = Set.of("1280w");
    private static final int MAX_DIMENSION = 10_000;

    private final MemberAccess memberAccess;
    private final ImageUploadRepository uploads;
    private final ImageStorage storage;
    private final ImageStorageProperties properties;
    private final UlidGenerator ulids;
    private final UuidGenerator uuids;
    private final ObjectMapper objectMapper;

    @Transactional
    public PresignedUpload createPresignedUpload(Long memberId, CreatePresignedUpload command) {
        memberAccess.active(memberId);
        validate(command);
        String imageId = ulids.next();
        String objectKeyId = uuids.next();
        Duration validFor = Duration.ofSeconds(properties.getPresignExpirySeconds());
        Map<String, Map<String, Object>> metadata = new LinkedHashMap<>();
        List<VariantUpload> uploadUrls = command.variants().stream().map(variant -> {
            String objectKey = objectKey(command.purpose(), objectKeyId, variant.name());
            metadata.put(variant.name(), Map.of(
                "objectKey", objectKey,
                "contentType", WEBP,
                "sizeBytes", variant.sizeBytes()));
            return new VariantUpload(variant.name(), objectKey,
                storage.presignPut(command.purpose(), objectKey, WEBP, variant.sizeBytes(), validFor));
        }).toList();
        uploads.save(new ImageUpload(imageId, memberId, command.purpose(), command.sourceWidth(), command.sourceHeight(),
            json(metadata), Instant.now().plusSeconds(properties.getUnusedRetentionSeconds())));
        return new PresignedUpload(imageId, uploadUrls, properties.getPresignExpirySeconds());
    }

    @Transactional
    public Verification verifyAndConsume(Long requesterId, String imageId) {
        ImageUpload upload = uploads.findById(imageId)
            .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
        if (!upload.getExpiresAt().isAfter(Instant.now())) {
            throw new DomainException(ErrorCode.NOT_FOUND);
        }
        if (!upload.getMemberId().equals(requesterId)) {
            throw new DomainException(ErrorCode.FORBIDDEN);
        }
        if (upload.isConsumed()) {
            throw new DomainException(ErrorCode.CONFLICT);
        }
        List<String> objectKeys = objectKeys(upload);
        if (!hasRequiredVariants(upload)
            || objectKeys.stream().anyMatch(key -> !isValidUpload(upload.getPurpose(), key))) {
            return new Verification(false, true, objectKeys);
        }
        if (uploads.consumeIfOwnedAndActive(imageId, requesterId, Instant.now()) != 1) {
            throw new DomainException(ErrorCode.CONFLICT);
        }
        return new Verification(true, true, objectKeys);
    }

    /**
     * 다른 도메인이 업로드 이미지를 연결할 때 소유자·용도·실제 객체 존재 여부를 확인하고 한 번만 소비한다.
     */
    @Transactional
    public List<String> consumeOwned(Long requesterId, ImagePurpose purpose, List<String> imageIds) {
        if (imageIds == null || imageIds.isEmpty()) {
            return List.of();
        }
        if (imageIds.stream().anyMatch(id -> id == null || id.isBlank())
            || imageIds.stream().distinct().count() != imageIds.size()) {
            throw new DomainException(ErrorCode.INVALID_INPUT);
        }

        Instant now = Instant.now();
        List<ImageUpload> selected = imageIds.stream().map(String::trim).map(imageId -> {
            ImageUpload upload = uploads.findById(imageId)
                .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
            if (!upload.getMemberId().equals(requesterId) || upload.getPurpose() != purpose) {
                throw new DomainException(ErrorCode.FORBIDDEN);
            }
            if (upload.isConsumed()) {
                throw new DomainException(ErrorCode.CONFLICT);
            }
            if (!upload.getExpiresAt().isAfter(now)) {
                throw new DomainException(ErrorCode.RESOURCE_EXPIRED);
            }
            if (!hasRequiredVariants(upload)) {
                throw new DomainException(ErrorCode.INVALID_INPUT);
            }
            if (objectKeys(upload).stream().anyMatch(key -> !isValidUpload(upload.getPurpose(), key))) {
                throw new BusinessRuleViolationException("업로드가 완료되지 않았거나 형식·크기가 올바르지 않은 이미지가 있습니다.");
            }
            return upload;
        }).toList();

        for (ImageUpload upload : selected) {
            if (uploads.consumeIfOwnedAndActive(upload.getId(), requesterId, now) != 1) {
                throw new DomainException(ErrorCode.CONCURRENT_UPDATE);
            }
        }
        return selected.stream().map(ImageUpload::getId).toList();
    }

    @Transactional
    public void deleteUnused(Long memberId, String imageId) {
        ImageUpload upload = uploads.findByIdForUpdate(imageId)
            .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
        if (!upload.getMemberId().equals(memberId)) {
            throw new DomainException(ErrorCode.FORBIDDEN);
        }
        if (upload.isConsumed()) {
            throw new DomainException(ErrorCode.CONFLICT);
        }
        objectKeys(upload).forEach(key -> storage.delete(upload.getPurpose(), key));
        uploads.delete(upload);
    }

    /**
     * 만료된 미사용 업로드를 잠근 뒤 S3 variant와 DB 메타데이터를 함께 정리한다.
     * 한 번에 최대 100건만 처리해 스케줄러 한 주기가 과도하게 길어지지 않게 한다.
     */
    @Transactional
    public int deleteExpiredUnused() {
        List<ImageUpload> expired = uploads
            .findTop100ByConsumedFalseAndExpiresAtLessThanEqualOrderByExpiresAtAsc(Instant.now());
        for (ImageUpload upload : expired) {
            objectKeys(upload).forEach(key -> storage.delete(upload.getPurpose(), key));
            uploads.delete(upload);
        }
        return expired.size();
    }

    private void validate(CreatePresignedUpload command) {
        if (command == null || command.variants() == null || command.purpose() == null
            || command.sourceWidth() <= 0 || command.sourceHeight() <= 0) {
            throw new DomainException(ErrorCode.INVALID_INPUT);
        }
        if (command.fileName() == null
            || !command.fileName().trim().toLowerCase(Locale.ROOT).endsWith(".webp")
            || !WEBP.equalsIgnoreCase(command.contentType())) {
            throw new DomainException(ErrorCode.INVALID_INPUT);
        }
        if (command.sourceWidth() > MAX_DIMENSION || command.sourceHeight() > MAX_DIMENSION) {
            throw new DomainException(ErrorCode.INVALID_INPUT);
        }
        if (command.variants().stream().anyMatch(variant -> variant == null || variant.name() == null)) {
            throw new DomainException(ErrorCode.INVALID_INPUT);
        }
        Set<String> requiredVariants = requiredVariants(command.purpose());
        List<String> variantNames = command.variants().stream().map(UploadVariant::name).toList();
        if (variantNames.stream().anyMatch(variant -> !PUBLIC_VARIANTS.contains(variant))
            || variantNames.stream().distinct().count() != variantNames.size()
            || !Set.copyOf(variantNames).equals(requiredVariants)) {
            throw new DomainException(ErrorCode.INVALID_INPUT);
        }
        if (command.variants().stream().anyMatch(variant -> variant.sizeBytes() <= 0
            || variant.sizeBytes() > properties.getMaxFileSizeBytes())) {
            throw new DomainException(ErrorCode.INVALID_INPUT);
        }
    }

    private String objectKey(ImagePurpose purpose, String objectKeyId, String variant) {
        String prefix = properties.getKeyPrefix().replaceAll("^/+|/+$", "");
        return prefix + "/" + purpose.name().toLowerCase() + "/" + objectKeyId + "/" + variant + ".webp";
    }

    private boolean isValidUpload(ImagePurpose purpose, String objectKey) {
        return storage.isValid(purpose, objectKey, WEBP, properties.getMaxFileSizeBytes());
    }

    private String json(Map<String, Map<String, Object>> variants) {
        try {
            return objectMapper.writeValueAsString(variants);
        } catch (Exception exception) {
            throw new IllegalStateException("이미지 variant를 저장할 수 없습니다.", exception);
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> objectKeys(ImageUpload upload) {
        return variants(upload).values().stream().map(this::objectKey).toList();
    }

    private boolean hasRequiredVariants(ImageUpload upload) {
        return variants(upload).keySet().equals(requiredVariants(upload.getPurpose()));
    }

    private Set<String> requiredVariants(ImagePurpose purpose) {
        return purpose == ImagePurpose.RETURN ? REQUIRED_RETURN_VARIANTS : REQUIRED_PUBLIC_VARIANTS;
    }

    private String requiredVariantsMessage(ImagePurpose purpose) {
        return purpose == ImagePurpose.RETURN
            ? "반품 이미지는 1280w variant를 한 번만 요청해야 합니다."
            : "이미지 variant는 320w, 640w, 1280w를 각각 한 번씩 요청해야 합니다.";
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> variants(ImageUpload upload) {
        try {
            return objectMapper.readValue(upload.getVariants(), Map.class);
        } catch (Exception exception) {
            throw new IllegalStateException("저장된 이미지 variant를 읽을 수 없습니다.", exception);
        }
    }

    private String objectKey(Object metadata) {
        if (metadata instanceof Map<?, ?> variant && variant.get("objectKey") != null) {
            return String.valueOf(variant.get("objectKey"));
        }
        // 기존 단일 문자열 variant 레코드는 만료 전까지 검증·삭제가 가능하도록 지원한다.
        return String.valueOf(metadata);
    }

    public record UploadVariant(String name, long sizeBytes) {}

    public record CreatePresignedUpload(String fileName, String contentType, ImagePurpose purpose,
                                        int sourceWidth, int sourceHeight, List<UploadVariant> variants) {}

    public record VariantUpload(String variant, String objectKey, String presignedUrl) {}

    public record PresignedUpload(String imageId, List<VariantUpload> uploads, int expiresInSeconds) {}

    public record Verification(boolean exists, boolean ownerMatched, List<String> variants) {}
}
