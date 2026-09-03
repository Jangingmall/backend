package com.jangingmall.backend.notification.application;

import com.jangingmall.backend.global.exception.NotFoundException;
import com.jangingmall.backend.notification.domain.Notification;
import com.jangingmall.backend.notification.infrastructure.NotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    // depth-3: 목록 조회 — 에러 없음 (빈 리스트 반환)
    @Transactional(readOnly = true)
    public List<NotificationResponse> findAll(Long memberId) {
        return notificationRepository.findByMemberIdOrderByCreatedAtDesc(memberId)
            .stream()
            .map(NotificationResponse::from)
            .toList();
    }

    // depth-3: 단건 조회 — NotFoundException (depth-4 경유 없음, Service에서 직접)
    @Transactional(readOnly = true)
    public NotificationResponse findOne(Long notificationId, Long requesterId) {
        Notification notification = findOrThrow(notificationId);       // → NotFoundException
        notification.validateOwnership(requesterId);                   // → ForbiddenException (depth-4)
        return NotificationResponse.from(notification);
    }

    // depth-3: 읽음 처리 — NotFoundException + ForbiddenException + BusinessRuleViolationException
    @Transactional
    public void markAsRead(Long notificationId, Long requesterId) {
        Notification notification = findOrThrow(notificationId);       // → NotFoundException
        notification.validateOwnership(requesterId);                   // → ForbiddenException (depth-4)
        notification.markAsRead();                                     // → BusinessRuleViolationException (depth-4)
    }

    // depth-3: 삭제 — NotFoundException + ForbiddenException + BusinessRuleViolationException
    @Transactional
    public void delete(Long notificationId, Long requesterId) {
        Notification notification = findOrThrow(notificationId);       // → NotFoundException
        notification.validateOwnership(requesterId);                   // → ForbiddenException (depth-4)
        notification.delete();                                         // → BusinessRuleViolationException (depth-4)
    }

    // depth-5: private 헬퍼 → NotFoundException
    private Notification findOrThrow(Long notificationId) {
        return notificationRepository.findById(notificationId)
            .orElseThrow(NotFoundException::new);
    }
}
