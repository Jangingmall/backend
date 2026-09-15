package com.jangingmall.backend.notification.application;

import jakarta.validation.constraints.NotBlank;

public record NotificationCreateRequest(
    @NotBlank String title,
    @NotBlank String content
) {}
