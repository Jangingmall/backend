package com.jangingmall.backend.product.application;

import com.jangingmall.backend.product.domain.Product;
import com.fasterxml.jackson.annotation.JsonInclude;
import tools.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.util.List;

public record ProductResponse(
    Long productId,
    Long artisanId,
    Long categoryId,
    String categoryName,
    Long subcategoryId,
    String subcategoryName,
    String title,
    String description,
    int price,
    int stock,
    String thumbnailUrl,
    String status,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    List<String> giftThemes,
    List<String> purposeTags,
    Integer productionPeriodDays,
    List<String> colors,
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    List<ProductImageView> images,
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    List<ContentBlockView> detailPageBlocks,
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    List<ImageVariantView> thumbnail,
    @JsonInclude(JsonInclude.Include.NON_NULL)
    JsonNode detailPageDocument,
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    List<FeatureView> detailFeatures
) {
    public ProductResponse(Long productId, Long artisanId, Long categoryId, String categoryName,
                           Long subcategoryId, String subcategoryName, String title, String description, int price,
                           int stock, String thumbnailUrl, String status, LocalDateTime createdAt,
                           LocalDateTime updatedAt, List<String> giftThemes, List<String> purposeTags,
                           Integer productionPeriodDays, List<String> colors) {
        this(productId, artisanId, categoryId, categoryName, subcategoryId, subcategoryName, title, description,
            price, stock, thumbnailUrl, status, createdAt, updatedAt, giftThemes, purposeTags,
            productionPeriodDays, colors, List.of(), List.of(), List.of(), null, List.of());
    }

    public static ProductResponse from(Product product) {
        return from(product, List.of(), List.of());
    }

    public static ProductResponse from(Product product, List<ProductImageView> images) {
        return from(product, images, List.of());
    }
    public static ProductResponse from(Product product, List<ProductImageView> images,
                                       List<ContentBlockView> detailPageBlocks) {
        return from(product, images, detailPageBlocks, null, List.of());
    }

    public static ProductResponse from(Product product, List<ProductImageView> images,
                                       List<ContentBlockView> detailPageBlocks, JsonNode detailPageDocument,
                                       List<FeatureView> detailFeatures) {
        List<ProductImageView> resolvedImages =
                images == null ? List.of() : List.copyOf(images);

        List<ImageVariantView> resolvedThumbnail =
                resolvedImages.isEmpty()
                        ? List.of()
                        : (resolvedImages.get(0).variants() == null
                        ? List.of()
                        : List.copyOf(resolvedImages.get(0).variants()));

        List<String> resolvedGiftThemes =
                product.getGiftThemes() == null
                        ? List.of()
                        : List.copyOf(product.getGiftThemes());

        List<String> resolvedPurposeTags =
                product.getPurposeTags() == null
                        ? List.of()
                        : List.copyOf(product.getPurposeTags());

        List<String> resolvedColors =
                product.getColors() == null
                        ? List.of()
                        : List.copyOf(product.getColors());

        String thumbnailUrl = product.getThumbnailUrl();

        if ((thumbnailUrl == null || thumbnailUrl.isBlank())
                && !resolvedImages.isEmpty()
                && resolvedImages.get(0).variants() != null
                && !resolvedImages.get(0).variants().isEmpty()) {
            thumbnailUrl = resolvedImages.get(0).variants().get(0).url();
        }

        return new ProductResponse(
                product.getId(),
                product.getArtisanId(),

                product.getCategory() != null
                        ? product.getCategory().getId()
                        : null,

                product.getCategory() != null
                        ? product.getCategory().getName()
                        : null,

                product.getSubcategory() != null
                        ? product.getSubcategory().getId()
                        : null,

                product.getSubcategory() != null
                        ? product.getSubcategory().getName()
                        : null,

                product.getTitle(),
                product.getDescription(),
                product.getPrice(),
                product.getStock(),
                thumbnailUrl,
                product.getStatus().name(),
                product.getCreatedAt(),
                product.getUpdatedAt(),

                resolvedGiftThemes,
                resolvedPurposeTags,
                product.getProductionPeriodDays(),
                resolvedColors,

                resolvedImages,

                detailPageBlocks == null
                        ? List.of()
                        : List.copyOf(detailPageBlocks),

                resolvedThumbnail,
                detailPageDocument,
                detailFeatures == null ? List.of() : List.copyOf(detailFeatures)
        );
    }

    /** 상세 JSON 의 특징 카드(제목 + 설명). 화면이 JSON 을 그리지 않아도 특징 목록을 바로 보여 줄 수 있다. */
    public record FeatureView(String title, String body) {}

    public record ProductImageView(String imageId, String alt, List<ImageVariantView> variants) {}

    public record ImageVariantView(String url, int width, int height, String format) {}

    public record ContentBlockView(int order, String tag, boolean hasImage,
                                   List<ImageVariantView> imageVariants, String videoUrl, String text) {}
}
