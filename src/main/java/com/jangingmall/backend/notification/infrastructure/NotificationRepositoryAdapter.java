package com.jangingmall.backend.notification.infrastructure;

import com.jangingmall.backend.notification.domain.Notification;
import com.jangingmall.backend.notification.domain.NotificationRepository;
import com.jangingmall.backend.notification.domain.NotificationStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
class NotificationRepositoryAdapter implements NotificationRepository {

    private final JpaNotificationRepository jpa;

    @Override
    public List<Notification> findByMemberIdOrderByCreatedAtDesc(Long memberId) {
        return jpa.findByMemberIdOrderByCreatedAtDesc(memberId);
    }

    @Override
    public List<Notification> findByMemberIdAndStatus(Long memberId, NotificationStatus status) {
        return jpa.findByMemberIdAndStatus(memberId, status);
    }

    @Override
    public long countByMemberIdAndStatus(Long memberId, NotificationStatus status) {
        return jpa.countByMemberIdAndStatus(memberId, status);
    }

    @Override
    public Optional<Notification> findById(Long id) {
        return jpa.findById(id);
    }

    @Override
    public Notification save(Notification notification) {
        return jpa.save(notification);
    }
}
