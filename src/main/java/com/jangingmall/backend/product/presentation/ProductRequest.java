package com.jangingmall.backend.product.presentation;

import com.fasterxml.jackson.annotation.JsonInclude;
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
        @NotBlank(message = "상품명은 필수입니다") @Size(max = 200, message = "상품명은 200자 이내여야 합니다") String title,
        String description,
        @Positive(message = "가격은 1 이상이어야 합니다") int price,
        @Min(value = 0, message = "재고는 0 이상이어야 합니다") int stock,
        @Size(max = 500, message = "썸네일 URL은 500자 이내여야 합니다") String thumbnailUrl,
        List<@Size(max = 50, message = "선물 테마는 50자 이내여야 합니다") String> giftThemes,
        List<@Size(max = 100, message = "용도 태그는 100자 이내여야 합니다") String> purposeTags,
        @Positive(message = "제작 기간은 1일 이상이어야 합니다") Integer productionPeriodDays,
        List<@Size(max = 20, message = "색상 코드는 20자 이내여야 합니다") String> colors,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        List<@NotBlank @Size(max = 30, message = "이미지 ID는 30자 이내여야 합니다") String> images
    ) implements ProductRequest {
        public Create(Long categoryId, Long subcategoryId, String title, String description, int price, int stock,
                      String thumbnailUrl, List<String> giftThemes, List<String> purposeTags,
                      Integer productionPeriodDays, List<String> colors) {
            this(categoryId, subcategoryId, title, description, price, stock, thumbnailUrl, giftThemes, purposeTags,
                productionPeriodDays, colors, null);
        }
    }

    record Update(
        Long categoryId,
        Long subcategoryId,
        @NotBlank(message = "상품명은 필수입니다") @Size(max = 200, message = "상품명은 200자 이내여야 합니다") String title,
        String description,
        @Positive(message = "가격은 1 이상이어야 합니다") int price,
        @Min(value = 0, message = "재고는 0 이상이어야 합니다") int stock,
        @Size(max = 500, message = "썸네일 URL은 500자 이내여야 합니다") String thumbnailUrl,
        List<@Size(max = 50, message = "선물 테마는 50자 이내여야 합니다") String> giftThemes,
        List<@Size(max = 100, message = "용도 태그는 100자 이내여야 합니다") String> purposeTags,
        @Positive(message = "제작 기간은 1일 이상이어야 합니다") Integer productionPeriodDays,
        List<@Size(max = 20, message = "색상 코드는 20자 이내여야 합니다") String> colors,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        List<@NotBlank @Size(max = 30, message = "이미지 ID는 30자 이내여야 합니다") String> images
    ) implements ProductRequest {
        public Update(Long categoryId, Long subcategoryId, String title, String description, int price, int stock,
                      String thumbnailUrl, List<String> giftThemes, List<String> purposeTags,
                      Integer productionPeriodDays, List<String> colors) {
            this(categoryId, subcategoryId, title, description, price, stock, thumbnailUrl, giftThemes, purposeTags,
                productionPeriodDays, colors, null);
        }
    }

    record ChangeStatus(
        @NotNull(message = "상태값은 필수입니다") String status
    ) implements ProductRequest {}
}
