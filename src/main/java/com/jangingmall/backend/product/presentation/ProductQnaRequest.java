package com.jangingmall.backend.product.presentation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public sealed interface ProductQnaRequest {

    record Ask(
        @NotBlank(message = "문의 내용은 필수입니다") @Size(max = 1000, message = "문의 내용은 1000자 이내여야 합니다") String content,
        @NotNull(message = "비공개 여부는 필수입니다") Boolean secret
    ) implements ProductQnaRequest {}

    record Answer(
        @NotBlank(message = "답변 내용은 필수입니다") @Size(max = 2000, message = "답변 내용은 2000자 이내여야 합니다") String content
    ) implements ProductQnaRequest {}
}
