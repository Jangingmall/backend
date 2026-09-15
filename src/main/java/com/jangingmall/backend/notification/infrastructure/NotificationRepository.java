package com.jangingmall.backend.notification.infrastructure;

import com.jangingmall.backend.notification.domain.Notification;
import com.jangingmall.backend.notification.domain.NotificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface JpaNotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByMemberIdOrderByCreatedAtDesc(Long memberId);
    List<Notification> findByMemberIdAndStatus(Long memberId, NotificationStatus status);
    long countByMemberIdAndStatus(Long memberId, NotificationStatus status);
}
