package com.jangingmall.backend.product.presentation;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public sealed interface ProductRequest {

    record Create(
        Long categoryId,
        Long subcategoryId,
        @NotBlank @Size(max = 200) String title,
        String description,
        @Positive int price,
        @Min(0) int stock,
        String thumbnailUrl,
        List<String> giftThemes,
        List<String> purposeTags,
        Integer productionPeriodDays,
        List<String> colors
    ) implements ProductRequest {}

    record Update(
        Long categoryId,
        Long subcategoryId,
        @NotBlank @Size(max = 200) String title,
        String description,
        @Positive int price,
        @Min(0) int stock,
        String thumbnailUrl,
        List<String> giftThemes,
        List<String> purposeTags,
        Integer productionPeriodDays,
        List<String> colors
    ) implements ProductRequest {}

    record ChangeStatus(
        @NotNull String status
    ) implements ProductRequest {}
}
