package com.jangingmall.backend.content.application;

import com.jangingmall.backend.content.domain.ContentGeneration;
import com.jangingmall.backend.content.domain.ContentGenerationRepository;
import com.jangingmall.backend.content.domain.GenerationErrorMessage;
import com.jangingmall.backend.content.domain.GenerationStatus;
import com.jangingmall.backend.global.exception.BusinessRuleViolationException;
import com.jangingmall.backend.global.exception.ConflictException;
import com.jangingmall.backend.global.exception.ExternalServiceException;
import com.jangingmall.backend.global.exception.ForbiddenException;
import com.jangingmall.backend.global.exception.NotFoundException;
import com.jangingmall.backend.image.application.ImageStorage;
import com.jangingmall.backend.image.domain.ImagePurpose;
import com.jangingmall.backend.product.domain.Product;
import com.jangingmall.backend.product.domain.ProductErrorMessage;
import com.jangingmall.backend.product.domain.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class GenerationService {

    private final ContentGenerationRepository generationRepository;
    private final ProductRepository productRepository;
    private final ContentService contentService;
    private final ImageStorage imageStorage;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;

    private static final Pattern PHOTO_FILENAME = Pattern.compile("-photo-\\d+-(.+)$");

    @Transactional
    public GenerationResponse request(GenerationCommand.Request command) {
        Product product = getProduct(command.productId());
        verifyOwner(product, command.requesterId());

        String imagesJson = String.join(",", command.images());
        ContentGeneration generation = ContentGeneration.create(
            command.productId(),
            imagesJson,
            command.productName(),
            command.howMade(),
            command.careTips()
        );
        ContentGeneration saved = generationRepository.save(generation);

        // 트랜잭션 커밋 후 @TransactionalEventListener(AFTER_COMMIT)으로 비동기 실행
        eventPublisher.publishEvent(new GenerationRequestedEvent(saved.getId(), command));
        return GenerationResponse.from(saved);
    }

    @Transactional
    public GenerationResponse complete(GenerationCommand.Complete command) {
        ContentGeneration generation = generationRepository.findById(command.generationId())
            .orElseThrow(() -> new NotFoundException(GenerationErrorMessage.NOT_FOUND.message()));
        generation.complete(command.reactDocumentJson(), command.idempotencyKey());
        ContentGeneration saved = generationRepository.save(generation);
        contentService.storeReactDocument(
            new ContentCommand.StoreReactDocument(saved.getProductId(), command.reactDocumentJson(), null)
        );
        log.info("AI 콜백 완료 generationId={}", command.generationId());
        return GenerationResponse.from(saved);
    }

    @Transactional
    public BeToAiPersistAckResponse completeWithImages(
        GenerationCommand.Complete command,
        MultipartFile detailPageImage,
        Map<String, MultipartFile> sectionFiles,
        Map<String, MultipartFile> photoFiles,
        String productIdStr
    ) {
        Optional<ContentGeneration> existing = generationRepository.findByIdempotencyKey(command.idempotencyKey());
        if (existing.isPresent() && existing.get().getStatus() == GenerationStatus.COMPLETED) {
            ContentGeneration gen = existing.get();
            return new BeToAiPersistAckResponse(gen.getId().toString(), productIdStr, "ALREADY_SAVED", gen.getCompletedAt());
        }

        // 같은 건의 콜백(GenAI 재전송 포함)이 동시에 들어와도 한 번에 하나만 저장되도록 행을 잠그고 상태를 다시 확인한다.
        ContentGeneration generation = generationRepository.findByIdForUpdate(command.generationId())
            .orElseThrow(() -> new NotFoundException(GenerationErrorMessage.NOT_FOUND.message()));
        if (generation.getStatus() == GenerationStatus.COMPLETED) {
            return new BeToAiPersistAckResponse(
                generation.getId().toString(), productIdStr, "ALREADY_SAVED", generation.getCompletedAt());
        }
        if (generation.getStatus() == GenerationStatus.FAILED) {
            // 마감(또는 AI 실패 확정) 뒤에 늦게 도착한 결과는 저장하지 않는다. 409면 GenAI도 재전송하지 않는다.
            log.warn("이미 FAILED 처리된 생성의 늦은 콜백을 거절합니다 generationId={}", command.generationId());
            throw new ConflictException(GenerationErrorMessage.ALREADY_FAILED.message());
        }

        uploadDetailPageImage(command.generationId().toString(), detailPageImage);
        uploadPrefixedFiles(command.generationId().toString(), "section-", sectionFiles);
        uploadPrefixedFiles(command.generationId().toString(), "photo-", photoFiles);

        generation.complete(command.reactDocumentJson(), command.idempotencyKey());
        ContentGeneration saved = generationRepository.save(generation);
        contentService.storeReactDocument(
            new ContentCommand.StoreReactDocument(saved.getProductId(), command.reactDocumentJson(), null)
        );
        log.info("AI 멀티파트 콜백 완료 generationId={}", command.generationId());
        return new BeToAiPersistAckResponse(command.generationId().toString(), productIdStr, "SAVED", saved.getCompletedAt());
    }

    /**
     * AI 초안이 끝난 생성 건의 최종 렌더링을 사용자가 수동으로 다시 요청한다. 자동 요청(스케줄러)과 같은 멱등성 키를
     * 쓰므로 이미 렌더 중이어도 중복 생성되지 않는다. 요청은 커밋 후 비동기로 GenAI에 전달된다.
     */
    @Transactional
    public GenerationResponse requestRender(Long productId, Long generationId, Long requesterId) {
        Product product = getProduct(productId);
        verifyOwner(product, requesterId);

        ContentGeneration generation = generationRepository.findByIdAndProductId(generationId, productId)
            .orElseThrow(() -> new NotFoundException(GenerationErrorMessage.NOT_FOUND.message()));
        if (generation.getStatus() != GenerationStatus.DRAFT_READY) {
            throw new BusinessRuleViolationException(GenerationErrorMessage.RENDER_NOT_ALLOWED.message());
        }
        eventPublisher.publishEvent(new AiRenderApprovalRequestedEvent(generation.getJobId(), generation.getId()));
        log.info("AI 렌더 수동 요청 generationId={} requesterId={}", generationId, requesterId);
        return GenerationResponse.from(generation);
    }

    @Transactional(readOnly = true)
    public GenerationResponse poll(Long productId, Long generationId, Long requesterId) {
        Product product = getProduct(productId);
        verifyOwner(product, requesterId);

        ContentGeneration generation = generationRepository.findByIdAndProductId(generationId, productId)
            .orElseThrow(() -> new NotFoundException(GenerationErrorMessage.NOT_FOUND.message()));
        return GenerationResponse.from(generation);
    }

    private Product getProduct(Long productId) {
        return productRepository.findById(productId)
            .orElseThrow(() -> new NotFoundException(ProductErrorMessage.NOT_FOUND.message()));
    }

    private void uploadDetailPageImage(String generationId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return;
        }
        try {
            String key = "ai-generated/" + generationId + "/detail-page." + extension(file.getContentType());
            imageStorage.put(ImagePurpose.PRODUCT, key, file.getContentType(), file.getBytes());
        } catch (IOException exception) {
            log.error("상세페이지 이미지 업로드 실패 generationId={}", generationId, exception);
            throw new ExternalServiceException("상세페이지 이미지 업로드 실패 generationId=" + generationId);
        }
    }

    private void uploadPrefixedFiles(String generationId, String keyPrefix, Map<String, MultipartFile> files) {
        for (Map.Entry<String, MultipartFile> entry : files.entrySet()) {
            MultipartFile file = entry.getValue();
            try {
                String fileId = extractFileId(entry.getKey(), file.getOriginalFilename());
                String key = "ai-generated/" + generationId + "/" + keyPrefix + fileId + "." + extension(file.getContentType());
                imageStorage.put(ImagePurpose.PRODUCT, key, file.getContentType(), file.getBytes());
            } catch (IOException exception) {
                log.error("파일 업로드 실패 generationId={} prefix={} filename={}", generationId, keyPrefix, file.getOriginalFilename(), exception);
                throw new ExternalServiceException("파일 업로드 실패 generationId=" + generationId + " prefix=" + keyPrefix);
            }
        }
    }

    private String extension(String contentType) {
        return switch (contentType == null ? "" : contentType) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            default -> "bin";
        };
    }

    /**
     * GenAI는 사진을 "{generationId}-photo-{NN}-{photo_id}.ext"로, 섹션을 "{generationId}-{NN}-{section_id}.ext"로 보낸다.
     * 사진은 photo_id 전체(예: detail-02)를 쓰고, 그 외에는 파트 이름의 순번(예: detail_page_section_01 → 01)을 써서
     * 서로 다른 파일이 같은 S3 키로 덮어써지지 않게 한다.
     */
    private String extractFileId(String partName, String filename) {
        if (filename != null) {
            Matcher matcher = PHOTO_FILENAME.matcher(filename.replaceAll("\\.[^.]+$", ""));
            if (matcher.find()) {
                return sanitizeKeyPart(matcher.group(1));
            }
        }
        int separator = partName.lastIndexOf('_');
        return sanitizeKeyPart(separator >= 0 ? partName.substring(separator + 1) : partName);
    }

    private static String sanitizeKeyPart(String value) {
        return value.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private void verifyOwner(Product product, Long requesterId) {
        if (!product.getArtisanId().equals(requesterId)) {
            throw new ForbiddenException(GenerationErrorMessage.FORBIDDEN.message());
        }
    }
}
