package com.jangingmall.backend.content.domain;

public record AiJobAccepted(
    String jobId,
    String requestId,
    String statusUrl
) {}
