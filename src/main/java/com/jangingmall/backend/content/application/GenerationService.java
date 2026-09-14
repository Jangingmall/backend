package com.jangingmall.backend.content.application;

import com.jangingmall.backend.content.domain.AiContentClient;
import com.jangingmall.backend.content.domain.BlockTag;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

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
            String generatedBlocks = aiContentClient.requestGeneration(
                generationId,
                command.productId(),
                command.images(),
                command.productName(),
                command.howMade(),
                command.careTips()
            );
            generation.complete(generatedBlocks);
            generationRepository.save(generation);
            materializeContent(command.productId(), generatedBlocks, command.requesterId());
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

    private void materializeContent(Long productId, String generatedBlocksJson, Long requesterId) {
        try {
            JsonNode array = objectMapper.readTree(generatedBlocksJson);
            List<ContentCommand.BlockInput> blockInputs = new ArrayList<>();
            for (JsonNode node : array) {
                BlockTag tag = BlockTag.valueOf(node.path("tag").asText("p"));
                blockInputs.add(new ContentCommand.BlockInput(
                    node.path("order").asInt(),
                    tag,
                    node.path("text").asText(null),
                    node.path("imageUrl").asText(null),
                    node.path("videoUrl").asText(null)
                ));
            }
            contentService.materializeFromBlocks(productId, blockInputs, requesterId);
        } catch (Exception exception) {
            log.error("콘텐츠 정규화 실패 productId={} reason={}", productId, exception.getMessage());
        }
    }
}
