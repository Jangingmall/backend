package com.jangingmall.backend.content.presentation;

import jakarta.validation.constraints.NotNull;
import tools.jackson.databind.JsonNode;

public sealed interface AiCallbackRequest permits AiCallbackRequest.Complete {

    record Complete(
        @NotNull(message = "react_document는 필수입니다")
        JsonNode reactDocument
    ) implements AiCallbackRequest {}
}
