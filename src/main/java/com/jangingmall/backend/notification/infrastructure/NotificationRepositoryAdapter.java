package com.jangingmall.backend.notification.infrastructure;

import com.jangingmall.backend.notification.domain.Notification;
import com.jangingmall.backend.notification.domain.NotificationRepository;
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
    public Optional<Notification> findById(Long id) {
        return jpa.findById(id);
    }

    @Override
    public Notification save(Notification notification) {
        return jpa.save(notification);
    }
}
