package com.jangingmall.backend.content.presentation;

import tools.jackson.databind.JsonNode;

public sealed interface AiCallbackRequest permits AiCallbackRequest.Persist {

    record Persist(
        String generationId,
        String jobId,
        String requestId,
        String idempotencyKey,
        String productId,
        JsonNode detailPageReactDocument
    ) implements AiCallbackRequest {}
}
