package com.jangingmall.backend.content.application;

import com.jangingmall.backend.content.domain.AiContentClient;
import com.jangingmall.backend.content.domain.ContentGeneration;
import com.jangingmall.backend.content.domain.ContentGenerationRepository;
import com.jangingmall.backend.content.domain.GenerationErrorMessage;
import com.jangingmall.backend.global.exception.ForbiddenException;
import com.jangingmall.backend.global.exception.NotFoundException;
import com.jangingmall.backend.product.domain.Product;
import com.jangingmall.backend.product.domain.ProductErrorMessage;
import com.jangingmall.backend.product.domain.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Service
@RequiredArgsConstructor
public class GenerationService {

    private final ContentGenerationRepository generationRepository;
    private final ProductRepository productRepository;
    private final AiContentClient aiContentClient;
    private final ContentService contentService;
    private final ObjectMapper objectMapper;

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

        executeAsync(saved.getId(), command);
        return GenerationResponse.from(saved);
    }

    @Transactional
    public GenerationResponse complete(GenerationCommand.Complete command) {
        ContentGeneration generation = generationRepository.findById(command.generationId())
            .orElseThrow(() -> new NotFoundException(GenerationErrorMessage.NOT_FOUND.message()));
        generation.complete(command.reactDocumentJson());
        ContentGeneration saved = generationRepository.save(generation);
        contentService.storeReactDocument(
            new ContentCommand.StoreReactDocument(saved.getProductId(), command.reactDocumentJson(), null)
        );
        log.info("AI 콜백 완료 generationId={}", command.generationId());
        return GenerationResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public GenerationResponse poll(Long productId, Long generationId, Long requesterId) {
        Product product = getProduct(productId);
        verifyOwner(product, requesterId);

        ContentGeneration generation = generationRepository.findByIdAndProductId(generationId, productId)
            .orElseThrow(() -> new NotFoundException(GenerationErrorMessage.NOT_FOUND.message()));
        return GenerationResponse.from(generation);
    }

    @Async
    public void executeAsync(Long generationId, GenerationCommand.Request command) {
        ContentGeneration generation = generationRepository.findByIdAndProductId(generationId, command.productId())
            .orElseThrow(() -> new NotFoundException(GenerationErrorMessage.NOT_FOUND.message()));
        try {
            String reactDocumentJson = aiContentClient.requestGeneration(
                generationId,
                command.productId(),
                command.images(),
                command.productName(),
                command.howMade(),
                command.careTips()
            );
            generation.complete(reactDocumentJson);
            generationRepository.save(generation);
            contentService.storeReactDocument(
                new ContentCommand.StoreReactDocument(command.productId(), reactDocumentJson, command.requesterId())
            );
            log.info("AI 콘텐츠 생성 완료 generationId={}", generationId);
        } catch (Exception exception) {
            generation.fail();
            generationRepository.save(generation);
            log.error("AI 콘텐츠 생성 실패 generationId={} reason={}", generationId, exception.getMessage());
        }
    }

    private Product getProduct(Long productId) {
        return productRepository.findById(productId)
            .orElseThrow(() -> new NotFoundException(ProductErrorMessage.NOT_FOUND.message()));
    }

    private void verifyOwner(Product product, Long requesterId) {
        if (!product.getArtisanId().equals(requesterId)) {
            throw new ForbiddenException(GenerationErrorMessage.FORBIDDEN.message());
        }
    }
}
