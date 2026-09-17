package com.jangingmall.backend.product.application;

import com.jangingmall.backend.product.domain.Product;
import com.fasterxml.jackson.annotation.JsonInclude;

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
    List<ImageVariantView> thumbnail
) {
    public ProductResponse(Long productId, Long artisanId, Long categoryId, String categoryName,
                           Long subcategoryId, String subcategoryName, String title, String description, int price,
                           int stock, String thumbnailUrl, String status, LocalDateTime createdAt,
                           LocalDateTime updatedAt, List<String> giftThemes, List<String> purposeTags,
                           Integer productionPeriodDays, List<String> colors) {
        this(productId, artisanId, categoryId, categoryName, subcategoryId, subcategoryName, title, description,
            price, stock, thumbnailUrl, status, createdAt, updatedAt, giftThemes, purposeTags,
            productionPeriodDays, colors, List.of(), List.of(), List.of());
    }

    public static ProductResponse from(Product product) {
        return from(product, List.of(), List.of());
    }

    public static ProductResponse from(Product product, List<ProductImageView> images) {
        return from(product, images, List.of());
    }

    public static ProductResponse from(Product product, List<ProductImageView> images,
                                       List<ContentBlockView> detailPageBlocks) {
        List<ProductImageView> resolvedImages = images == null ? List.of() : List.copyOf(images);
        List<ImageVariantView> resolvedThumbnail = resolvedImages.isEmpty()
            ? List.of()
            : (resolvedImages.get(0).variants() == null ? List.of() : resolvedImages.get(0).variants());
        String thumbnailUrl = product.getThumbnailUrl();
        if ((thumbnailUrl == null || thumbnailUrl.isBlank()) && !resolvedImages.isEmpty()
            && resolvedImages.get(0).variants() != null && !resolvedImages.get(0).variants().isEmpty()) {
            thumbnailUrl = resolvedImages.get(0).variants().get(0).url();
        }
        return new ProductResponse(
            product.getId(),
            product.getArtisanId(),
            product.getCategory() != null ? product.getCategory().getId() : null,
            product.getCategory() != null ? product.getCategory().getName() : null,
            product.getSubcategory() != null ? product.getSubcategory().getId() : null,
            product.getSubcategory() != null ? product.getSubcategory().getName() : null,
            product.getTitle(),
            product.getDescription(),
            product.getPrice(),
            product.getStock(),
            thumbnailUrl,
            product.getStatus().name(),
            product.getCreatedAt(),
            product.getUpdatedAt(),
            product.getGiftThemes(),
            product.getPurposeTags(),
            product.getProductionPeriodDays(),
            product.getColors(),
            resolvedImages,
            detailPageBlocks == null ? List.of() : List.copyOf(detailPageBlocks),
            resolvedThumbnail
        );
    }

    public record ProductImageView(String imageId, String alt, List<ImageVariantView> variants) {}

    public record ImageVariantView(String url, int width, int height, String format) {}

    public record ContentBlockView(int order, String tag, boolean hasImage,
                                   List<ImageVariantView> imageVariants, String videoUrl, String text) {}
}
