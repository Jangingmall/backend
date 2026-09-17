package com.jangingmall.backend.content.application;

import java.time.LocalDateTime;

public record BeToAiPersistAckResponse(
    String generationId,
    String productId,
    String status,
    LocalDateTime savedAt
) {}
