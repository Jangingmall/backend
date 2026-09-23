package com.jangingmall.backend.image.application;

import com.jangingmall.backend.global.exception.BusinessRuleViolationException;
import com.jangingmall.backend.global.exception.DomainException;
import com.jangingmall.backend.global.exception.ErrorCode;
import com.jangingmall.backend.image.domain.ImagePurpose;
import com.jangingmall.backend.image.domain.ImageUpload;
import com.jangingmall.backend.image.domain.ImageUploadRepository;
import com.jangingmall.backend.member.application.MemberAccess;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${image.base-url:}")
    private String imageBaseUrl;

    @Transactional
    public PresignedUpload createPresignedUpload(Long memberId, CreatePresignedUpload command) {
        memberAccess.active(memberId);
        validate(command);
        String imageId = ulids.next();
        Duration validFor = Duration.ofSeconds(properties.getPresignExpirySeconds());
        String base = normalizedImageBaseUrl();
        Map<String, Map<String, Object>> metadata = new LinkedHashMap<>();
        List<VariantUpload> uploadUrls = command.variants().stream().map(variant -> {
            String objectKey = objectKey(memberId, imageId, command.purpose(), variant.name());
            metadata.put(variant.name(), Map.of(
                "objectKey", objectKey,
                "contentType", WEBP,
                "sizeBytes", variant.sizeBytes()));
            return new VariantUpload(
                variant.name(),
                objectKey,
                storage.presignPut(command.purpose(), objectKey, WEBP, variant.sizeBytes(), validFor),
                publicViewUrl(command.purpose(), objectKey, base)
            );
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
        if (imageIds.stream().anyMatch(id -> id == null || id.isBlank())) {
            throw new DomainException(ErrorCode.INVALID_INPUT);
        }
        List<String> normalizedIds = imageIds.stream().map(String::trim)
            .map(this::resolveImageReference).toList();
        if (normalizedIds.stream().distinct().count() != normalizedIds.size()) {
            throw new DomainException(ErrorCode.INVALID_INPUT);
        }

        Instant now = Instant.now();
        List<ImageUpload> selected = normalizedIds.stream().map(imageId -> {
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

    /**
     * The return API historically called this value {@code returnPhotoKeys} and
     * sent an S3 object key. Resolve that legacy reference to the image group
     * before applying the same ownership and consumption checks as imageId.
     */
    private String resolveImageReference(String reference) {
        if (uploads.findById(reference).isPresent()) {
            return reference;
        }
        return findImageIdByReference(reference).orElse(reference);
    }

    /**
     * Converts a consumed image aggregate into the public contract's three
     * size-specific WebP variants.
     */
    public List<PublicVariant> publicVariants(String imageId) {
        ImageUpload upload = uploads.findById(imageId)
            .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
        if (!upload.isConsumed()) {
            throw new DomainException(ErrorCode.CONFLICT);
        }
        Map<String, Object> stored = variants(upload);
        String base = normalizedImageBaseUrl();
        List<PublicVariant> result = PUBLIC_VARIANTS.stream().map(name -> {
            Object metadata = stored.get(name);
            if (metadata == null) {
                throw new DomainException(ErrorCode.INVALID_INPUT);
            }
            String key = objectKey(metadata);
            int width = Integer.parseInt(name.substring(0, name.length() - 1));
            int height = (int) Math.round((double) width / upload.getSourceWidth() * upload.getSourceHeight());
            String url = publicUrl(key, base, upload.getPurpose());
            return new PublicVariant(url, width, height, "webp");
        }).toList();
        return List.copyOf(result);
    }

    /** Resolves an AI/editor imageUrl back to the owning imageId. */
    public Optional<String> findImageIdByReference(String reference) {
        if (reference == null || reference.isBlank()) {
            return Optional.empty();
        }
        List<ImageUpload> all = uploads.findAll();
        if (all == null) {
            return Optional.empty();
        }
        for (ImageUpload upload : all) {
            Map<String, Object> stored = variants(upload);
            for (Object metadata : stored.values()) {
                String key = objectKey(metadata);
                String url = publicUrl(key, normalizedImageBaseUrl(), upload.getPurpose());
                if (reference.equals(key) || reference.equals(url) || reference.endsWith("/" + key)) {
                    return Optional.of(upload.getId());
                }
            }
        }
        return Optional.empty();
    }

    private String publicUrl(String key, String base, ImagePurpose purpose) {
        if (!base.isBlank()) {
            return base + "/" + key.replaceAll("^/+", "");
        }
        String bucket = purpose == ImagePurpose.RETURN ? properties.getReturnBucket() : properties.getBucket();
        if (bucket != null && !bucket.isBlank()) {
            return "https://" + bucket + ".s3." + properties.getRegion() + ".amazonaws.com/" + key;
        }
        return key;
    }

    /**
     * The return bucket remains private. Public image purposes can be rendered as soon as their WebP variants are
     * uploaded, while return images must be read through an authenticated endpoint instead of a public URL.
     */
    private String publicViewUrl(ImagePurpose purpose, String key, String base) {
        if (purpose == ImagePurpose.RETURN) {
            return null;
        }
        String url = publicUrl(key, base, purpose);
        return url.equals(key) ? null : url;
    }

    private String normalizedImageBaseUrl() {
        return imageBaseUrl == null ? "" : imageBaseUrl.replaceAll("/+$", "");
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
        if (command.fileName() == null || !WEBP.equalsIgnoreCase(command.contentType())) {
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
        if (command.variants().stream().anyMatch(variant -> variant.sizeBytes() < 0
            || variant.sizeBytes() > properties.getMaxFileSizeBytes())) {
            throw new DomainException(ErrorCode.INVALID_INPUT);
        }
    }

    private String objectKey(Long memberId, String imageId, ImagePurpose purpose, String variant) {
        String prefix = properties.getKeyPrefix().replaceAll("^/+|/+$", "");
        return prefix + "/" + purpose.name().toLowerCase() + "/" + memberId + "/" + imageId + "/" + variant + ".webp";
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

    public record VariantUpload(String variant, String objectKey, String presignedUrl, String viewUrl) {
        public VariantUpload(String variant, String objectKey, String presignedUrl) {
            this(variant, objectKey, presignedUrl, null);
        }

        /** New collaboration-contract name. presignedUrl remains serialized for existing clients. */
        @JsonProperty("uploadUrl")
        public String uploadUrl() {
            return presignedUrl;
        }
    }

    public record PresignedUpload(String imageId, List<VariantUpload> uploads, int expiresInSeconds) {
        /**
         * Contract name used by new clients. Keep {@code uploads} serialized during the migration so clients that
         * adopted the original endpoint do not break.
         */
        @JsonProperty("variants")
        public List<VariantUpload> variants() {
            return uploads;
        }
    }

    public record Verification(boolean exists, boolean ownerMatched, List<String> variants) {}

    public record PublicVariant(String url, int width, int height, String format) {}
}
