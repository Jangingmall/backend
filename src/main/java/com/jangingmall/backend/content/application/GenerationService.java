package com.jangingmall.backend.content.application;

import com.jangingmall.backend.content.domain.ContentGeneration;
import com.jangingmall.backend.content.domain.ContentGenerationRepository;
import com.jangingmall.backend.content.domain.GenerationErrorMessage;
import com.jangingmall.backend.content.domain.GenerationStatus;
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

        ContentGeneration generation = generationRepository.findById(command.generationId())
            .orElseThrow(() -> new NotFoundException(GenerationErrorMessage.NOT_FOUND.message()));

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
        try {
            String key = "ai-generated/" + generationId + "/detail-page." + extension(file.getContentType());
            imageStorage.put(ImagePurpose.PRODUCT, key, file.getContentType(), file.getBytes());
        } catch (IOException exception) {
            log.error("상세페이지 이미지 업로드 실패 generationId={}", generationId, exception);
        }
    }

    private void uploadPrefixedFiles(String generationId, String keyPrefix, Map<String, MultipartFile> files) {
        for (Map.Entry<String, MultipartFile> entry : files.entrySet()) {
            MultipartFile file = entry.getValue();
            try {
                String fileId = extractPhotoId(file.getOriginalFilename());
                String key = "ai-generated/" + generationId + "/" + keyPrefix + fileId + "." + extension(file.getContentType());
                imageStorage.put(ImagePurpose.PRODUCT, key, file.getContentType(), file.getBytes());
            } catch (IOException exception) {
                log.error("파일 업로드 실패 generationId={} prefix={} filename={}", generationId, keyPrefix, file.getOriginalFilename(), exception);
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

    private String extractPhotoId(String filename) {
        if (filename == null) {
            return "unknown";
        }
        String[] parts = filename.replaceAll("\\.[^.]+$", "").split("-");
        return parts[parts.length - 1];
    }

    private void verifyOwner(Product product, Long requesterId) {
        if (!product.getArtisanId().equals(requesterId)) {
            throw new ForbiddenException(GenerationErrorMessage.FORBIDDEN.message());
        }
    }
}
