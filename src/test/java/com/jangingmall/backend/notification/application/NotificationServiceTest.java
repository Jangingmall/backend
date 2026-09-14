package com.jangingmall.backend.notification.application;

import com.jangingmall.backend.global.exception.ForbiddenException;
import com.jangingmall.backend.global.exception.NotFoundException;
import com.jangingmall.backend.notification.domain.Notification;
import com.jangingmall.backend.notification.domain.NotificationRepository;
import com.jangingmall.backend.notification.domain.NotificationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(notificationRepository);
    }

    private Notification createNotification(Long id, Long memberId) {
        Notification notification = Notification.create(memberId, "알림 제목", "알림 내용");
        ReflectionTestUtils.setField(notification, "id", id);
        return notification;
    }

    @Test
    @DisplayName("목록 조회 시 회원의 알림 목록을 반환한다")
    void findAll_returnsNotifications() {
        Notification n1 = createNotification(1L, 1L);
        Notification n2 = createNotification(2L, 1L);
        when(notificationRepository.findByMemberIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(n1, n2));

        List<NotificationResponse> result = notificationService.findAll(1L);

        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("단건 조회 시 알림이 없으면 NotFoundException이 발생한다")
    void findOne_notFound() {
        when(notificationRepository.findById(anyLong())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.findOne(999L, 1L))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("단건 조회 시 소유자가 아니면 ForbiddenException이 발생한다")
    void findOne_forbidden() {
        Notification notification = createNotification(1L, 1L);
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(notification));

        assertThatThrownBy(() -> notificationService.findOne(1L, 99L))
            .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("단건 조회 시 소유자이면 알림 정보를 반환한다")
    void findOne_success() {
        Notification notification = createNotification(1L, 1L);
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(notification));

        NotificationResponse result = notificationService.findOne(1L, 1L);

        assertThat(result.title()).isEqualTo("알림 제목");
        assertThat(result.status()).isEqualTo(NotificationStatus.UNREAD);
    }

    @Test
    @DisplayName("읽음 처리 시 알림 상태가 READ로 변경된다")
    void markAsRead_success() {
        Notification notification = createNotification(1L, 1L);
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(notification));

        notificationService.markAsRead(1L, 1L);

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.READ);
    }

    @Test
    @DisplayName("삭제 처리 시 알림 상태가 DELETED로 변경된다")
    void delete_success() {
        Notification notification = createNotification(1L, 1L);
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(notification));

        notificationService.delete(1L, 1L);

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.DELETED);
    }

    @Test
    @DisplayName("삭제 시 알림이 없으면 NotFoundException이 발생한다")
    void delete_notFound() {
        when(notificationRepository.findById(anyLong())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.delete(999L, 1L))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("읽지 않은 알림 수를 반환한다")
    void countUnread_returnsCount() {
        when(notificationRepository.countByMemberIdAndStatus(1L, NotificationStatus.UNREAD)).thenReturn(3L);

        UnreadCountResponse result = notificationService.countUnread(1L);

        assertThat(result.unreadCount()).isEqualTo(3L);
    }

    @Test
    @DisplayName("읽지 않은 알림이 없으면 0을 반환한다")
    void countUnread_returnsZeroWhenNone() {
        when(notificationRepository.countByMemberIdAndStatus(1L, NotificationStatus.UNREAD)).thenReturn(0L);

        UnreadCountResponse result = notificationService.countUnread(1L);

        assertThat(result.unreadCount()).isZero();
    }

    @Test
    @DisplayName("전체 읽음 처리 시 UNREAD 알림이 모두 READ로 변경된다")
    void markAllAsRead_marksAllUnread() {
        Notification n1 = createNotification(1L, 1L);
        Notification n2 = createNotification(2L, 1L);
        when(notificationRepository.findByMemberIdAndStatus(1L, NotificationStatus.UNREAD))
            .thenReturn(List.of(n1, n2));

        notificationService.markAllAsRead(1L);

        assertThat(n1.getStatus()).isEqualTo(NotificationStatus.READ);
        assertThat(n2.getStatus()).isEqualTo(NotificationStatus.READ);
    }

    @Test
    @DisplayName("전체 읽음 처리 시 UNREAD 알림이 없으면 아무것도 변경되지 않는다")
    void markAllAsRead_doesNothingWhenNoUnread() {
        when(notificationRepository.findByMemberIdAndStatus(1L, NotificationStatus.UNREAD))
            .thenReturn(List.of());

        notificationService.markAllAsRead(1L);

        verify(notificationRepository).findByMemberIdAndStatus(eq(1L), eq(NotificationStatus.UNREAD));
    }
}
