package com.jangingmall.backend.notification.application;

import com.jangingmall.backend.global.exception.NotFoundException;
import com.jangingmall.backend.notification.domain.Notification;
import com.jangingmall.backend.notification.domain.NotificationErrorMessage;
import com.jangingmall.backend.notification.domain.NotificationRepository;
import com.jangingmall.backend.notification.domain.NotificationStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;

    @Transactional(readOnly = true)
    public List<NotificationResponse> findAll(Long memberId) {
        return notificationRepository.findByMemberIdOrderByCreatedAtDesc(memberId)
            .stream()
            .map(NotificationResponse::from)
            .toList();
    }

    @Transactional(readOnly = true)
    public NotificationResponse findOne(Long notificationId, Long requesterId) {
        Notification notification = findOrThrow(notificationId);
        notification.validateOwnership(requesterId);
        return NotificationResponse.from(notification);
    }

    @Transactional
    public void markAsRead(Long notificationId, Long requesterId) {
        Notification notification = findOrThrow(notificationId);
        notification.validateOwnership(requesterId);
        notification.markAsRead();
    }

    @Transactional
    public void delete(Long notificationId, Long requesterId) {
        Notification notification = findOrThrow(notificationId);
        notification.validateOwnership(requesterId);
        notification.delete();
    }

    @Transactional(readOnly = true)
    public UnreadCountResponse countUnread(Long memberId) {
        long count = notificationRepository.countByMemberIdAndStatus(memberId, NotificationStatus.UNREAD);
        return UnreadCountResponse.of(count);
    }

    @Transactional
    public void markAllAsRead(Long memberId) {
        List<Notification> unread = notificationRepository.findByMemberIdAndStatus(memberId, NotificationStatus.UNREAD);
        unread.forEach(Notification::markAsRead);
    }

    private Notification findOrThrow(Long notificationId) {
        return notificationRepository.findById(notificationId)
            .orElseThrow(() -> new NotFoundException(NotificationErrorMessage.NOT_FOUND.message()));
    }
}
