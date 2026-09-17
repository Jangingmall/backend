package com.jangingmall.backend.product.presentation;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public sealed interface ProductReviewRequest {

    record Write(
        @NotNull(message = "주문 항목 ID는 필수입니다") Long orderItemId,
        @Min(value = 1, message = "평점은 1 이상이어야 합니다") @Max(value = 5, message = "평점은 5 이하여야 합니다") short rating,
        @NotBlank(message = "후기 내용은 필수입니다") @Size(max = 2000, message = "후기 내용은 2000자 이내여야 합니다") String content
    ) implements ProductReviewRequest {}
}
