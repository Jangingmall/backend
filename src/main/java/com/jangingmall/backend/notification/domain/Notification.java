package com.jangingmall.backend.notification.domain;

import com.jangingmall.backend.global.exception.BusinessRuleViolationException;
import com.jangingmall.backend.global.exception.ForbiddenException;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long memberId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationStatus status;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected Notification() {}

    private Notification(Long memberId, String title, String content) {
        this.memberId = memberId;
        this.title = title;
        this.content = content;
        this.status = NotificationStatus.UNREAD;
        this.createdAt = LocalDateTime.now();
    }

    public static Notification create(Long memberId, String title, String content) {
        return new Notification(memberId, title, content);
    }

    // depth-4: Entity 상태 전이 검증 — BusinessRuleViolationException
    public void markAsRead() {
        if (this.status == NotificationStatus.DELETED) {
            throw new BusinessRuleViolationException(NotificationErrorMessage.ALREADY_DELETED_READ);
        }
        this.status = NotificationStatus.READ;
    }

    // depth-4: 소유권 검증 — ForbiddenException
    public void validateOwnership(Long requesterId) {
        if (!this.memberId.equals(requesterId)) {
            throw new ForbiddenException(NotificationErrorMessage.NOT_OWNER);
        }
    }

    // depth-4: 삭제 상태 전이 — BusinessRuleViolationException
    public void delete() {
        if (this.status == NotificationStatus.DELETED) {
            throw new BusinessRuleViolationException(NotificationErrorMessage.ALREADY_DELETED);
        }
        this.status = NotificationStatus.DELETED;
    }

    public Long id() { return id; }
    public Long memberId() { return memberId; }
    public String title() { return title; }
    public String content() { return content; }
    public NotificationStatus status() { return status; }
    public LocalDateTime createdAt() { return createdAt; }
}
