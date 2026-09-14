package com.jangingmall.backend.product.presentation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public sealed interface ProductQnaRequest {

    record Ask(
        @NotBlank @Size(max = 1000) String content,
        @NotNull Boolean secret
    ) implements ProductQnaRequest {}

    record Answer(
        @NotBlank @Size(max = 2000) String content
    ) implements ProductQnaRequest {}
}
