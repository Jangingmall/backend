package com.jangingmall.backend.notification.domain;

import java.util.List;
import java.util.Optional;

public interface NotificationRepository {
    List<Notification> findByMemberIdOrderByCreatedAtDesc(Long memberId);
    List<Notification> findByMemberIdAndStatus(Long memberId, NotificationStatus status);
    long countByMemberIdAndStatus(Long memberId, NotificationStatus status);
    Optional<Notification> findById(Long id);
    Notification save(Notification notification);
}
