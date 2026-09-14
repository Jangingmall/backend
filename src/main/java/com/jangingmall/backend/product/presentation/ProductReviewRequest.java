package com.jangingmall.backend.product.presentation;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public sealed interface ProductReviewRequest {

    record Write(
        @NotNull Long orderItemId,
        @Min(1) @Max(5) short rating,
        @NotBlank @Size(max = 2000) String content
    ) implements ProductReviewRequest {}
}
