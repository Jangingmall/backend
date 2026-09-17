package com.jangingmall.backend.content.application;

import com.jangingmall.backend.content.domain.AiContentClient;
import com.jangingmall.backend.content.domain.AiProductSyncPayload;
import com.jangingmall.backend.content.domain.Content;
import com.jangingmall.backend.content.domain.ContentBlock;
import com.jangingmall.backend.content.domain.ContentEditHistory;
import com.jangingmall.backend.content.domain.ContentEditHistoryRepository;
import com.jangingmall.backend.content.domain.ContentErrorMessage;
import com.jangingmall.backend.content.domain.ContentRepository;
import com.jangingmall.backend.content.domain.EditedByType;
import com.jangingmall.backend.content.domain.Interview;
import com.jangingmall.backend.content.domain.InterviewRepository;
import com.jangingmall.backend.global.exception.BusinessRuleViolationException;
import com.jangingmall.backend.global.exception.NotFoundException;
import com.jangingmall.backend.member.domain.ArtisanProfile;
import com.jangingmall.backend.member.domain.ArtisanProfileRepository;
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
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContentService {

    private static final String SCHEMA_VERSION = "2.0";
    private static final int CANVAS_WIDTH = 774;

    private final ContentRepository contentRepository;
    private final ContentEditHistoryRepository historyRepository;
    private final ProductRepository productRepository;
    private final AiContentClient aiContentClient;
    private final ArtisanProfileRepository artisanProfileRepository;
    private final InterviewRepository interviewRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public ContentResponse.Detail getContent(Long productId, Long requesterId) {
        verifyProductOwner(productId, requesterId);
        Content content = contentRepository.findByProductId(productId)
            .orElseThrow(() -> new NotFoundException(ContentErrorMessage.NOT_FOUND.message()));
        return ContentResponse.Detail.from(content);
    }

    @Transactional(readOnly = true)
    public List<ContentResponse.VersionHistory> getVersionHistory(Long productId, Long requesterId) {
        verifyProductOwner(productId, requesterId);
        Content content = contentRepository.findByProductId(productId)
            .orElseThrow(() -> new NotFoundException(ContentErrorMessage.NOT_FOUND.message()));

        return historyRepository.findAllByContentIdOrderByVersionAsc(content.getId()).stream()
            .map(ContentResponse.VersionHistory::from)
            .toList();
    }

    @Transactional
    public Content storeReactDocument(ContentCommand.StoreReactDocument command) {
        Content content = contentRepository.findByProductId(command.productId())
            .orElseGet(() -> {
                Content created = Content.create(command.productId());
                return contentRepository.save(created);
            });

        content.storeReactDocument(command.reactDocumentJson());
        Content saved = contentRepository.save(content);

        historyRepository.save(ContentEditHistory.record(
            saved.getId(), saved.getVersion(), EditedByType.AI, command.requesterId()
        ));

        return saved;
    }

    @Transactional
    public ContentResponse.StatusChanged submitForReview(ContentCommand.SubmitForReview command) {
        verifyProductOwner(command.productId(), command.requesterId());
        Content content = contentRepository.findByIdAndProductId(command.contentId(), command.productId())
            .orElseThrow(() -> new NotFoundException(ContentErrorMessage.NOT_FOUND.message()));
        content.submitForReview();
        return ContentResponse.StatusChanged.from(contentRepository.save(content));
    }

    @Transactional
    public ContentResponse.StatusChanged approve(ContentCommand.Approve command) {
        verifyProductOwner(command.productId(), command.requesterId());
        Content content = contentRepository.findByIdAndProductId(command.contentId(), command.productId())
            .orElseThrow(() -> new NotFoundException(ContentErrorMessage.NOT_FOUND.message()));
        content.approve(command.factCheckConfirmed(), command.photoMatchConfirmed(), command.displayApprovalBadge());
        return ContentResponse.StatusChanged.from(contentRepository.save(content));
    }

    @Transactional
    public ContentResponse.StatusChanged reject(ContentCommand.Reject command) {
        verifyProductOwner(command.productId(), command.requesterId());
        Content content = contentRepository.findByIdAndProductId(command.contentId(), command.productId())
            .orElseThrow(() -> new NotFoundException(ContentErrorMessage.NOT_FOUND.message()));
        content.reject();
        return ContentResponse.StatusChanged.from(contentRepository.save(content));
    }

    @Transactional
    public ContentResponse.StatusChanged publish(ContentCommand.Publish command) {
        verifyProductOwner(command.productId(), command.requesterId());
        Content content = contentRepository.findByProductId(command.productId())
            .orElseThrow(() -> new NotFoundException(ContentErrorMessage.NOT_FOUND.message()));
        content.publish();
        ContentResponse.StatusChanged result = ContentResponse.StatusChanged.from(contentRepository.save(content));
        syncPublishedProductToAi(command.productId());
        return result;
    }

    @Async
    public void syncPublishedProductToAi(Long productId) {
        try {
            Product product = productRepository.findById(productId)
                .orElseThrow(() -> new NotFoundException(ProductErrorMessage.NOT_FOUND.message()));
            ArtisanProfile artisan = artisanProfileRepository.findById(product.getArtisanId())
                .orElseThrow(() -> new NotFoundException("장인 프로필을 찾을 수 없습니다"));
            Optional<Interview> interview = interviewRepository.findByProductId(productId);

            AiProductSyncPayload payload = buildSyncPayload(product, artisan, interview);
            aiContentClient.syncProduct(payload);
        } catch (Exception e) {
            log.error("AI 상품 동기화 트리거 실패 productId={} reason={}", productId, e.getMessage());
        }
    }

    private AiProductSyncPayload buildSyncPayload(Product product, ArtisanProfile artisan, Optional<Interview> interview) {
        String makingStory = interview.map(Interview::getProcess).orElse("");
        String usageCare = interview.map(Interview::getMaterials).orElse("");
        String categoryName = product.getCategory() != null ? product.getCategory().getName() : null;

        AiProductSyncPayload.ArtisanInfo artisanInfo = new AiProductSyncPayload.ArtisanInfo(
            artisan.getId(), artisan.getBusinessName(), artisan.getCertificationLevel(), artisan.getIntroduction()
        );
        AiProductSyncPayload.ProductInfo productInfo = new AiProductSyncPayload.ProductInfo(
            product.getId(), product.getTitle(), categoryName,
            product.getMaterial(), product.getPrice(),
            product.getGiftThemes(), product.getPurposeTags(),
            makingStory, usageCare, product.getProductionPeriodDays(), product.getColors()
        );
        return new AiProductSyncPayload(artisanInfo, productInfo);
    }

    @Transactional
    public ContentResponse.BulkUpdated bulkUpdate(ContentCommand.BulkUpdate command) {
        verifyProductOwner(command.productId(), command.requesterId());
        Content content = contentRepository.findByIdAndProductId(command.contentId(), command.productId())
            .orElseThrow(() -> new NotFoundException(ContentErrorMessage.NOT_FOUND.message()));
        content.verifyEditable();
        String newDocument = buildReactDocument(command.blocks());
        content.storeReactDocument(newDocument);
        Content saved = contentRepository.save(content);
        historyRepository.save(ContentEditHistory.record(saved.getId(), saved.getVersion(), EditedByType.ARTISAN, command.requesterId()));
        List<ContentResponse.BlockView> blockViews = command.blocks().stream()
            .map(ContentResponse.BlockView::from)
            .toList();
        return new ContentResponse.BulkUpdated(saved.getId(), saved.getProductId(), saved.getStatus(), saved.getVersion(), blockViews);
    }

    @Transactional
    public ContentResponse.BlockUpdated updateBlock(ContentCommand.BlockUpdate command) {
        verifyProductOwner(command.productId(), command.requesterId());
        Content content = contentRepository.findByIdAndProductId(command.contentId(), command.productId())
            .orElseThrow(() -> new NotFoundException(ContentErrorMessage.NOT_FOUND.message()));
        content.verifyEditable();
        List<ContentBlock> blocks = extractBlocks(content.getReactDocument());
        ContentBlock target = blocks.stream()
            .filter(b -> b.order() == command.blockOrder())
            .findFirst()
            .orElseThrow(() -> new NotFoundException(ContentErrorMessage.BLOCK_NOT_FOUND.message()));
        ContentBlock updated = new ContentBlock(
            target.order(),
            command.block().tag() != null ? command.block().tag() : target.tag(),
            command.block().text() != null ? command.block().text() : target.text(),
            command.block().imageUrl() != null ? command.block().imageUrl() : target.imageUrl()
        );
        List<ContentBlock> updatedBlocks = blocks.stream()
            .map(b -> b.order() == command.blockOrder() ? updated : b)
            .toList();
        content.storeReactDocument(buildReactDocument(updatedBlocks));
        Content saved = contentRepository.save(content);
        historyRepository.save(ContentEditHistory.record(saved.getId(), saved.getVersion(), EditedByType.ARTISAN, command.requesterId()));
        return new ContentResponse.BlockUpdated(saved.getId(), saved.getVersion(), ContentResponse.BlockView.from(updated));
    }

    private String buildReactDocument(List<ContentBlock> blocks) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("schemaVersion", SCHEMA_VERSION);
        root.put("canvasWidth", CANVAS_WIDTH);
        ArrayNode rootArray = root.putArray("root");
        for (ContentBlock block : blocks) {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("tag", block.tag().name());
            node.putObject("props");
            ArrayNode children = node.putArray("children");
            if (block.tag().name().equals("img") || block.tag().name().equals("video")) {
                ObjectNode child = objectMapper.createObjectNode();
                child.put("tag", "text");
                ObjectNode props = child.putObject("props");
                props.put("value", block.imageUrl() != null ? block.imageUrl() : "");
                child.putArray("children");
                children.add(child);
            } else {
                ObjectNode child = objectMapper.createObjectNode();
                child.put("tag", "text");
                ObjectNode props = child.putObject("props");
                props.put("value", block.text() != null ? block.text() : "");
                child.putArray("children");
                children.add(child);
            }
            rootArray.add(node);
        }
        return objectMapper.writeValueAsString(root);
    }

    private List<ContentBlock> extractBlocks(String reactDocumentJson) {
        if (reactDocumentJson == null || reactDocumentJson.isBlank()) {
            return new ArrayList<>();
        }
        JsonNode document = objectMapper.readTree(reactDocumentJson);
        JsonNode rootArray = document.path("root");
        List<ContentBlock> result = new ArrayList<>();
        int order = 0;
        for (JsonNode node : rootArray) {
            String tag = node.path("tag").asText("p");
            String text = null;
            String imageUrl = null;
            JsonNode children = node.path("children");
            if (!children.isEmpty()) {
                JsonNode firstChild = children.get(0);
                String value = firstChild.path("props").path("value").asText(null);
                if (tag.equals("img") || tag.equals("video")) {
                    imageUrl = value;
                } else {
                    text = value;
                }
            }
            result.add(new ContentBlock(order++, com.jangingmall.backend.content.domain.BlockTag.valueOf(tag), text, imageUrl));
        }
        return result;
    }

    private void verifyProductOwner(Long productId, Long requesterId) {
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new NotFoundException(ProductErrorMessage.NOT_FOUND.message()));
        product.verifyOwner(requesterId);
    }
}
