package com.jangingmall.backend.notification.application;

import com.jangingmall.backend.notification.domain.Notification;
import com.jangingmall.backend.notification.domain.NotificationStatus;

import java.time.LocalDateTime;

public record NotificationResponse(
    Long id,
    String title,
    String content,
    NotificationStatus status,
    LocalDateTime createdAt
) {
    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
            notification.id(),
            notification.title(),
            notification.content(),
            notification.status(),
            notification.createdAt()
        );
    }
}
