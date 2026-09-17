package com.jangingmall.backend.content.application;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

public record BeToAiPersistAckResponse(
    @JsonProperty("generation_id") String generationId,
    @JsonProperty("product_id") String productId,
    String status,
    @JsonProperty("saved_at") LocalDateTime savedAt
) {}
