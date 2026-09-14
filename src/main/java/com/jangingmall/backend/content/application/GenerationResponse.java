package com.jangingmall.backend.content.application;

import com.jangingmall.backend.content.domain.ContentGeneration;
import com.jangingmall.backend.content.domain.GenerationStatus;

import java.time.LocalDateTime;

public record GenerationResponse(
    Long generationId,
    Long productId,
    GenerationStatus status,
    LocalDateTime requestedAt,
    LocalDateTime completedAt
) {
    public static GenerationResponse from(ContentGeneration generation) {
        return new GenerationResponse(
            generation.getId(),
            generation.getProductId(),
            generation.getStatus(),
            generation.getRequestedAt(),
            generation.getCompletedAt()
        );
    }
}
