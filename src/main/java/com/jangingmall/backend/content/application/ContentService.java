package com.jangingmall.backend.content.application;

import com.jangingmall.backend.content.domain.AiContentClient;
import com.jangingmall.backend.content.domain.AiProductSyncPayload;
import com.jangingmall.backend.content.domain.Content;
import com.jangingmall.backend.content.domain.ContentEditHistory;
import com.jangingmall.backend.content.domain.ContentEditHistoryRepository;
import com.jangingmall.backend.content.domain.ContentErrorMessage;
import com.jangingmall.backend.content.domain.ContentRepository;
import com.jangingmall.backend.content.domain.ContentBlock;
import com.jangingmall.backend.content.domain.ContentBlockRepository;
import com.jangingmall.backend.content.domain.EditedByType;
import com.jangingmall.backend.content.domain.Interview;
import com.jangingmall.backend.content.domain.InterviewRepository;
import com.jangingmall.backend.global.exception.NotFoundException;
import com.jangingmall.backend.global.exception.DomainException;
import com.jangingmall.backend.global.exception.ErrorCode;
import com.jangingmall.backend.image.application.ImageService;
import com.jangingmall.backend.image.domain.ImageUploadRepository;
import com.jangingmall.backend.member.domain.ArtisanProfile;
import com.jangingmall.backend.member.domain.ArtisanProfileRepository;
import com.jangingmall.backend.product.domain.Product;
import com.jangingmall.backend.product.domain.ProductErrorMessage;
import com.jangingmall.backend.product.domain.ProductRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.Objects;
import java.util.Set;

@Slf4j
@Service
public class ContentService {

    private final ContentRepository contentRepository;
    private final ContentEditHistoryRepository historyRepository;
    private final ProductRepository productRepository;
    private final AiContentClient aiContentClient;
    private final ArtisanProfileRepository artisanProfileRepository;
    private final InterviewRepository interviewRepository;
    private final ContentBlockRepository contentBlockRepository;
    private final ImageUploadRepository imageUploadRepository;
    private final ImageService imageService;
    private final ObjectMapper objectMapper;

    @Autowired
    public ContentService(ContentRepository contentRepository, ContentEditHistoryRepository historyRepository,
                          ProductRepository productRepository, AiContentClient aiContentClient,
                          ArtisanProfileRepository artisanProfileRepository, InterviewRepository interviewRepository,
                          ContentBlockRepository contentBlockRepository, ImageUploadRepository imageUploadRepository,
                          ImageService imageService, ObjectMapper objectMapper) {
        this.contentRepository = contentRepository;
        this.historyRepository = historyRepository;
        this.productRepository = productRepository;
        this.aiContentClient = aiContentClient;
        this.artisanProfileRepository = artisanProfileRepository;
        this.interviewRepository = interviewRepository;
        this.contentBlockRepository = contentBlockRepository;
        this.imageUploadRepository = imageUploadRepository;
        this.imageService = imageService;
        this.objectMapper = objectMapper;
    }

    public ContentService(ContentRepository contentRepository, ContentEditHistoryRepository historyRepository,
                          ProductRepository productRepository, AiContentClient aiContentClient,
                          ArtisanProfileRepository artisanProfileRepository, InterviewRepository interviewRepository) {
        this(contentRepository, historyRepository, productRepository, aiContentClient, artisanProfileRepository,
            interviewRepository, null, null, null, null);
    }

    @Transactional(readOnly = true)
    public ContentResponse.Detail getContent(Long productId, Long requesterId) {
        verifyProductOwner(productId, requesterId);
        Content content = contentRepository.findByProductId(productId)
            .orElseThrow(() -> new NotFoundException(ContentErrorMessage.NOT_FOUND.message()));
        return ContentResponse.Detail.from(content, readBlocks(content));
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

        replaceBlocks(saved, command.reactDocumentJson());

        historyRepository.save(ContentEditHistory.record(
            saved.getId(), saved.getVersion(), EditedByType.AI, command.requesterId()
        ));

        return saved;
    }

    @Transactional
    public ContentResponse.Detail replaceBlocks(ContentCommand.ReplaceBlocks command) {
        verifyProductOwner(command.productId(), command.requesterId());
        Content content = contentRepository.findByIdAndProductId(command.contentId(), command.productId())
            .orElseThrow(() -> new NotFoundException(ContentErrorMessage.NOT_FOUND.message()));
        Content saved = persistBlocks(content, command.blocks(), command.requesterId());
        return ContentResponse.Detail.from(saved, readBlocks(saved));
    }

    @Transactional
    public ContentResponse.BlockChanged updateBlock(ContentCommand.UpdateBlock command) {
        verifyProductOwner(command.productId(), command.requesterId());
        Content content = contentRepository.findByIdAndProductId(command.contentId(), command.productId())
            .orElseThrow(() -> new NotFoundException(ContentErrorMessage.NOT_FOUND.message()));
        if (contentBlockRepository == null) {
            throw new NotFoundException(ContentErrorMessage.NOT_FOUND.message());
        }
        ContentBlock block = contentBlockRepository
            .findByContentIdAndDisplayOrder(content.getId(), command.order())
            .orElseThrow(() -> new NotFoundException(ContentErrorMessage.NOT_FOUND.message()));
        ContentCommand.ContentBlockInput input = command.block();
        String tag = input.tag() == null ? block.getTag() : input.tag();
        validateTag(tag);
        String imageId = "img".equals(tag)
            ? (input.imageUrl() == null ? block.getImageId() : resolveAndConsumeImage(input.imageUrl(), command.requesterId()))
            : null;
        block.update(tag, imageId, "video".equals(tag) ? input.videoUrl() : null,
            Set.of("h2", "p").contains(tag) ? input.text() : null);
        content.storeReactDocument(serializeBlocks(contentBlockRepository.findByContentIdOrderByDisplayOrderAsc(content.getId())));
        Content saved = contentRepository.save(content);
        historyRepository.save(ContentEditHistory.record(saved.getId(), saved.getVersion(), EditedByType.ARTISAN,
            command.requesterId()));
        ContentResponse.Block updated = readBlocks(saved).stream()
            .filter(candidate -> candidate.order() == command.order())
            .findFirst()
            .orElseThrow(() -> new NotFoundException(ContentErrorMessage.NOT_FOUND.message()));
        return new ContentResponse.BlockChanged(saved.getId(), saved.getVersion(), updated);
    }

    private List<ContentResponse.Block> readBlocks(Content content) {
        if (contentBlockRepository == null) {
            return List.of();
        }
        return contentBlockRepository.findByContentIdOrderByDisplayOrderAsc(content.getId()).stream()
            .map(this::toResponseBlock)
            .toList();
    }

    private ContentResponse.Block toResponseBlock(ContentBlock block) {
        List<ContentResponse.ImageVariant> variants = block.getImageId() == null || imageService == null
            ? null
            : imageService.publicVariants(block.getImageId()).stream()
                .map(image -> new ContentResponse.ImageVariant(image.url(), image.width(), image.height(), image.format()))
                .toList();
        return new ContentResponse.Block(block.getDisplayOrder(), block.getTag(), block.getImageId() != null,
            variants, block.getVideoUrl(), block.getText());
    }

    @SuppressWarnings("unchecked")
    private void replaceBlocks(Content content, String reactDocumentJson) {
        if (contentBlockRepository == null || objectMapper == null || content.getId() == null
            || reactDocumentJson == null || reactDocumentJson.isBlank()) {
            return;
        }
        try {
            Map<String, Object> document = objectMapper.readValue(reactDocumentJson, Map.class);
            Object raw = document.get("root");
            if (!(raw instanceof List<?>)) {
                raw = document.get("blocks");
            }
            List<Map<?, ?>> rawBlocks = new ArrayList<>();
            collectReactBlocks(raw, rawBlocks);
            List<ContentBlock> blocks = new ArrayList<>();
            for (int index = 0; index < rawBlocks.size(); index++) {
                Map<?, ?> values = rawBlocks.get(index);
                String tag = text(values.get("tag"), "p");
                if (!Set.of("h2", "p", "img", "video").contains(tag)) {
                    continue;
                }
                int order = number(values.get("order"), index + 1);
                Object props = values.get("props");
                String imageRef = text(values.get("imageId"), text(values.get("imageUrl"),
                    props instanceof Map<?, ?> map ? text(map.get("imageId"), text(map.get("imageUrl"),
                        text(map.get("src"), text(map.get("url"), null)))) : null));
                String imageId = "img".equals(tag) ? resolveImageId(imageRef) : null;
                if ("img".equals(tag) && imageRef != null && !imageRef.isBlank() && imageId == null) {
                    throw new DomainException(ErrorCode.NOT_FOUND);
                }
                String videoUrl = "video".equals(tag) ? text(values.get("videoUrl"), null) : null;
                String blockText = Set.of("h2", "p").contains(tag)
                    ? text(values.get("text"), props instanceof Map<?, ?> map ? text(map.get("value"), null) : null)
                    : null;
                if (blockText == null && Set.of("h2", "p").contains(tag)) {
                    blockText = extractText(values.get("children"));
                }
                blocks.add(new ContentBlock(content.getId(), order, tag, imageId, videoUrl, blockText));
            }
            contentBlockRepository.deleteByContentId(content.getId());
            if (!blocks.isEmpty()) {
                contentBlockRepository.saveAll(blocks);
            }
        } catch (DomainException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("reactDocument 블록을 저장할 수 없습니다.", exception);
        }
    }

    private String resolveImageId(String imageRef) {
        if (imageRef == null || imageRef.isBlank() || imageUploadRepository == null) {
            return null;
        }
        var direct = imageUploadRepository.findById(imageRef).map(upload -> upload.getId());
        if (direct.isPresent()) {
            return direct.get();
        }
        return imageService == null ? null : imageService.findImageIdByReference(imageRef).orElse(null);
    }

    private void collectReactBlocks(Object raw, List<Map<?, ?>> blocks) {
        if (raw instanceof Map<?, ?> values) {
            String tag = text(values.get("tag"), "");
            if (Set.of("h2", "p", "img", "video").contains(tag)) {
                blocks.add(values);
            }
            collectReactBlocks(values.get("children"), blocks);
            return;
        }
        if (raw instanceof List<?> values) {
            values.forEach(value -> collectReactBlocks(value, blocks));
        }
    }

    private String extractText(Object raw) {
        if (raw instanceof Map<?, ?> values) {
            String tag = text(values.get("tag"), "");
            Object props = values.get("props");
            if ("text".equals(tag) && props instanceof Map<?, ?> map) {
                return text(map.get("value"), null);
            }
            return extractText(values.get("children"));
        }
        if (raw instanceof List<?> values) {
            return values.stream().map(this::extractText).filter(Objects::nonNull).reduce("", String::concat);
        }
        return null;
    }

    private Content persistBlocks(Content content, List<ContentCommand.ContentBlockInput> inputs, Long requesterId) {
        if (contentBlockRepository == null) {
            return content;
        }
        if (inputs == null || inputs.isEmpty()) {
            throw new DomainException(ErrorCode.INVALID_INPUT);
        }
        Set<Integer> orders = new java.util.HashSet<>();
        inputs.forEach(input -> {
            if (input == null || input.order() == null || input.order() < 1 || !orders.add(input.order())) {
                throw new DomainException(ErrorCode.INVALID_INPUT);
            }
            String candidateTag = input.tag() == null ? "p" : input.tag();
            validateTag(candidateTag);
            if ("img".equals(candidateTag) && (input.imageUrl() == null || input.imageUrl().isBlank())) {
                throw new DomainException(ErrorCode.INVALID_INPUT);
            }
            if ("video".equals(candidateTag) && (input.videoUrl() == null || input.videoUrl().isBlank())) {
                throw new DomainException(ErrorCode.INVALID_INPUT);
            }
        });
        List<ContentBlock> blocks = inputs.stream().map(input -> {
            String tag = input.tag() == null ? "p" : input.tag();
            validateTag(tag);
            String imageId = "img".equals(tag) ? resolveAndConsumeImage(input.imageUrl(), requesterId) : null;
            return new ContentBlock(content.getId(), input.order() == null ? 1 : input.order(), tag, imageId,
                "video".equals(tag) ? input.videoUrl() : null,
                Set.of("h2", "p").contains(tag) ? input.text() : null);
        }).toList();
        contentBlockRepository.deleteByContentId(content.getId());
        contentBlockRepository.saveAll(blocks);
        content.storeReactDocument(serializeBlocks(blocks));
        Content saved = contentRepository.save(content);
        historyRepository.save(ContentEditHistory.record(saved.getId(), saved.getVersion(), EditedByType.ARTISAN,
            requesterId));
        return saved;
    }

    private String resolveAndConsumeImage(String imageRef, Long requesterId) {
        if (imageRef == null || imageRef.isBlank()) {
            return null;
        }
        if (imageUploadRepository == null) {
            return imageRef;
        }
        String imageId = imageUploadRepository.findById(imageRef).map(upload -> upload.getId())
            .orElseGet(() -> imageService == null ? null : imageService.findImageIdByReference(imageRef).orElse(null));
        if (imageId == null) {
            throw new DomainException(ErrorCode.NOT_FOUND);
        }
        var upload = imageUploadRepository.findById(imageId)
            .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
        if (!upload.getMemberId().equals(requesterId)) {
            throw new DomainException(ErrorCode.FORBIDDEN);
        }
        if (!upload.isConsumed()) {
            if (imageService == null) {
                throw new DomainException(ErrorCode.INVALID_INPUT);
            }
            imageService.consumeOwned(requesterId, com.jangingmall.backend.image.domain.ImagePurpose.CONTENT,
                List.of(imageId));
        }
        return upload.getId();
    }

    private void validateTag(String tag) {
        if (!Set.of("h2", "p", "img", "video").contains(tag)) {
            throw new DomainException(ErrorCode.INVALID_INPUT);
        }
    }

    private String serializeBlocks(List<ContentBlock> blocks) {
        if (objectMapper == null) {
            return null;
        }
        try {
            List<Map<String, Object>> values = blocks.stream().map(block -> {
                Map<String, Object> value = new LinkedHashMap<>();
                value.put("order", block.getDisplayOrder());
                value.put("tag", block.getTag());
                value.put("imageId", block.getImageId());
                value.put("videoUrl", block.getVideoUrl());
                value.put("text", block.getText());
                return value;
            }).toList();
            return objectMapper.writeValueAsString(Map.of("schemaVersion", "2.0", "root", values));
        } catch (Exception exception) {
            throw new IllegalStateException("콘텐츠 블록을 직렬화할 수 없습니다.", exception);
        }
    }

    private String text(Object value, String fallback) {
        return value == null ? fallback : Objects.toString(value, fallback);
    }

    private int number(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? fallback : Integer.parseInt(value.toString());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
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

    private void verifyProductOwner(Long productId, Long requesterId) {
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new NotFoundException(ProductErrorMessage.NOT_FOUND.message()));
        product.verifyOwner(requesterId);
    }
}
