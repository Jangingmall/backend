package com.jangingmall.backend.notification.domain;

import com.jangingmall.backend.global.exception.BusinessRuleViolationException;
import com.jangingmall.backend.global.exception.ForbiddenException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.LocalDateTime;

@Entity
@Table(name = "notifications")
@Getter
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

    public void markAsRead() {
        if (this.status == NotificationStatus.DELETED) {
            throw new BusinessRuleViolationException(NotificationErrorMessage.ALREADY_DELETED_READ.message());
        }
        this.status = NotificationStatus.READ;
    }

    public void validateOwnership(Long requesterId) {
        if (!this.memberId.equals(requesterId)) {
            throw new ForbiddenException(NotificationErrorMessage.NOT_OWNER.message());
        }
    }

    public void delete() {
        if (this.status == NotificationStatus.DELETED) {
            throw new BusinessRuleViolationException(NotificationErrorMessage.ALREADY_DELETED.message());
        }
        this.status = NotificationStatus.DELETED;
    }
}
