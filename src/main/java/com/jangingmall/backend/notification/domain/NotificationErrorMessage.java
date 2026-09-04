package com.jangingmall.backend.notification.domain;

final class NotificationErrorMessage {

    static final String ALREADY_DELETED_READ = "삭제된 알림은 읽음 처리할 수 없습니다";
    static final String ALREADY_DELETED = "이미 삭제된 알림입니다";
    static final String NOT_OWNER = "본인의 알림만 접근할 수 있습니다";

    private NotificationErrorMessage() {}
}
