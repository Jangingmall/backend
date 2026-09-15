package com.jangingmall.backend.notification.application;

public record NotificationCreatedEvent(
    Long memberId,
    NotificationResponse notification
) {}
