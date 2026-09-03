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
            throw new BusinessRuleViolationException("삭제된 알림은 읽음 처리할 수 없습니다");
        }
        this.status = NotificationStatus.READ;
    }

    // depth-4: 소유권 검증 — ForbiddenException
    public void validateOwnership(Long requesterId) {
        if (!this.memberId.equals(requesterId)) {
            throw new ForbiddenException("본인의 알림만 접근할 수 있습니다");
        }
    }

    // depth-4: 삭제 상태 전이 — BusinessRuleViolationException
    public void delete() {
        if (this.status == NotificationStatus.DELETED) {
            throw new BusinessRuleViolationException("이미 삭제된 알림입니다");
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
