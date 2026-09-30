package com.jangingmall.backend.content.application;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
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
    /** 생성이 FAILED일 때 FE가 대체 이미지로 쓰도록 내려주는 고정 URL. */
    public static final String FAILED_FALLBACK_IMAGE_URL = "http://test.webp";

    /** FAILED일 때만 고정 대체 이미지 URL을 포함한다. 그 외 상태에서는 필드 자체가 생략된다. */
    @JsonProperty("fallbackImageUrl")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String fallbackImageUrl() {
        return status == GenerationStatus.FAILED ? FAILED_FALLBACK_IMAGE_URL : null;
    }

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
