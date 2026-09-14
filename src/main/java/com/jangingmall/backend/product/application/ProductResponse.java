package com.jangingmall.backend.product.application;

import com.jangingmall.backend.product.domain.Product;

import java.time.LocalDateTime;

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
    LocalDateTime updatedAt
) {
    public static ProductResponse from(Product product) {
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
            product.getThumbnailUrl(),
            product.getStatus().name(),
            product.getCreatedAt(),
            product.getUpdatedAt()
        );
    }
}
