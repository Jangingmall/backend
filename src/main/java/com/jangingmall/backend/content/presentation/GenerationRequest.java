package com.jangingmall.backend.content.presentation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public sealed interface GenerationRequest permits GenerationRequest.Create {

    record Create(
        @NotEmpty(message = "이미지 목록은 비워둘 수 없습니다")
        List<@NotBlank String> images,

        @NotBlank(message = "상품명은 필수입니다")
        @Size(max = 255, message = "상품명은 255자 이내여야 합니다")
        String productName,

        @NotBlank(message = "제작 과정은 필수입니다")
        String howMade,

        @NotBlank(message = "관리 방법은 필수입니다")
        String careTips
    ) implements GenerationRequest {}
}
