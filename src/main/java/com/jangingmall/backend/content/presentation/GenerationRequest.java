package com.jangingmall.backend.content.presentation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public sealed interface GenerationRequest permits GenerationRequest.Create {

    record Create(
        @NotEmpty(message = "이미지 목록은 비워둘 수 없습니다")
        @Size(max = 8, message = "이미지는 최대 8장까지 첨부할 수 있습니다")
        List<@NotBlank String> images,

        @NotBlank(message = "작품명은 필수입니다")
        @Size(max = 15, message = "작품명은 15자 이내여야 합니다")
        String productName,

        @NotBlank(message = "제작 과정은 필수입니다")
        String howMade,

        @NotBlank(message = "관리 방법은 필수입니다")
        String careTips
    ) implements GenerationRequest {}
}
