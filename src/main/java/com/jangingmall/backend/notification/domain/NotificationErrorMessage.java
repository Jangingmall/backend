package com.jangingmall.backend.notification.domain;

public enum NotificationErrorMessage {
    ALREADY_DELETED_READ("삭제된 알림은 읽음 처리할 수 없습니다"),
    ALREADY_DELETED("이미 삭제된 알림입니다"),
    NOT_OWNER("본인의 알림만 접근할 수 있습니다"),
    NOT_FOUND("알림을 찾을 수 없습니다");

    private final String text;

    NotificationErrorMessage(String text) {
        this.text = text;
    }

    public String message() {
        return text;
    }
}
