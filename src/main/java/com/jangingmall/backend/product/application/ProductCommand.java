package com.jangingmall.backend.product.application;

import java.util.List;

public sealed interface ProductCommand {

    record Create(
        Long artisanId,
        Long categoryId,
        Long subcategoryId,
        String title,
        String description,
        int price,
        int stock,
        String thumbnailUrl,
        List<String> giftThemes,
        List<String> purposeTags,
        Integer productionPeriodDays,
        List<String> colors
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
        String thumbnailUrl,
        List<String> giftThemes,
        List<String> purposeTags,
        Integer productionPeriodDays,
        List<String> colors
    ) implements ProductCommand {}

    record ChangeStatus(
        Long productId,
        Long requesterId,
        String status
    ) implements ProductCommand {}
}
