package com.jangingmall.backend.content.presentation;

import jakarta.validation.constraints.NotBlank;

public sealed interface AiCallbackRequest permits AiCallbackRequest.Complete {

    record Complete(
        @NotBlank(message = "blocks는 필수입니다")
        String blocks
    ) implements AiCallbackRequest {}
}
