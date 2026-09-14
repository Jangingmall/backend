package com.jangingmall.backend.product.application;

public sealed interface ProductCommand {

    record Create(
        Long artisanId,
        Long categoryId,
        Long subcategoryId,
        String title,
        String description,
        int price,
        int stock,
        String thumbnailUrl
    ) implements ProductCommand {}

    record Update(
        Long productId,
        Long requesterId,
        Long categoryId,
        Long subcategoryId,
        String title,
        String description,
        int price,
        int stock,
        String thumbnailUrl
    ) implements ProductCommand {}

    record ChangeStatus(
        Long productId,
        Long requesterId,
        String status
    ) implements ProductCommand {}
}
