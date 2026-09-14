package com.jangingmall.backend.notification.domain;

import com.jangingmall.backend.global.exception.BusinessRuleViolationException;
import com.jangingmall.backend.global.exception.ForbiddenException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotificationTest {

    @Test
    @DisplayName("알림 생성 시 UNREAD 상태로 초기화된다")
    void create_initialStatusIsUnread() {
        Notification notification = Notification.create(1L, "주문 완료", "주문이 접수되었습니다.");

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.UNREAD);
        assertThat(notification.getMemberId()).isEqualTo(1L);
        assertThat(notification.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("읽음 처리 시 READ 상태로 변경된다")
    void markAsRead_changesStatusToRead() {
        Notification notification = Notification.create(1L, "알림", "내용");

        notification.markAsRead();

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.READ);
    }

    @Test
    @DisplayName("삭제된 알림을 읽음 처리하면 BusinessRuleViolationException이 발생한다")
    void markAsRead_deletedNotification_throwsException() {
        Notification notification = Notification.create(1L, "알림", "내용");
        notification.delete();

        assertThatThrownBy(notification::markAsRead)
            .isInstanceOf(BusinessRuleViolationException.class)
            .hasMessageContaining("삭제된 알림");
    }

    @Test
    @DisplayName("삭제 시 DELETED 상태로 변경된다")
    void delete_changesStatusToDeleted() {
        Notification notification = Notification.create(1L, "알림", "내용");

        notification.delete();

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.DELETED);
    }

    @Test
    @DisplayName("이미 삭제된 알림을 다시 삭제하면 BusinessRuleViolationException이 발생한다")
    void delete_alreadyDeleted_throwsException() {
        Notification notification = Notification.create(1L, "알림", "내용");
        notification.delete();

        assertThatThrownBy(notification::delete)
            .isInstanceOf(BusinessRuleViolationException.class)
            .hasMessageContaining("이미 삭제된");
    }

    @Test
    @DisplayName("소유자 검증 시 memberId가 다르면 ForbiddenException이 발생한다")
    void validateOwnership_notOwner_throwsException() {
        Notification notification = Notification.create(1L, "알림", "내용");

        assertThatThrownBy(() -> notification.validateOwnership(99L))
            .isInstanceOf(ForbiddenException.class)
            .hasMessageContaining("본인의 알림");
    }

    @Test
    @DisplayName("소유자 검증 시 memberId가 같으면 예외가 발생하지 않는다")
    void validateOwnership_owner_doesNotThrow() {
        Notification notification = Notification.create(1L, "알림", "내용");

        notification.validateOwnership(1L);
    }
}
