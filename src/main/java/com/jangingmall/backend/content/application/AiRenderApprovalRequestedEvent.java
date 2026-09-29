package com.jangingmall.backend.content.application;

public record AiRenderApprovalRequestedEvent(
    String jobId,
    Long generationId
) {}
