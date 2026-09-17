package com.jangingmall.backend.notification.application;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record NotificationCreateRequest(
    @NotBlank(message = "알림 제목은 필수입니다") @Size(max = 255, message = "알림 제목은 255자 이내여야 합니다") String title,
    @NotBlank(message = "알림 내용은 필수입니다") @Size(max = 255, message = "알림 내용은 255자 이내여야 합니다") String content
) {}
