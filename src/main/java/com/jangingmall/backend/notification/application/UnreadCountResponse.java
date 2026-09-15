package com.jangingmall.backend.notification.application;

public record UnreadCountResponse(long unreadCount) {
    public static UnreadCountResponse of(long count) {
        return new UnreadCountResponse(count);
    }
}
