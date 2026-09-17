package com.jangingmall.backend.content.domain;

import com.jangingmall.backend.global.exception.BusinessRuleViolationException;
import com.jangingmall.backend.global.exception.ForbiddenException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "content")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Content {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "content_id")
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ContentStatus status;

    @Version
    @Column(nullable = false)
    private int version;

    @Column(name = "fact_check_confirmed", nullable = false)
    private boolean factCheckConfirmed;

    @Column(name = "photo_match_confirmed", nullable = false)
    private boolean photoMatchConfirmed;

    @Column(name = "display_approval_badge", nullable = false)
    private boolean displayApprovalBadge;

    @Column(name = "react_document", columnDefinition = "TEXT")
    private String reactDocument;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static Content create(Long productId) {
        Content content = new Content();
        content.productId = productId;
        content.status = ContentStatus.DRAFT;
        content.factCheckConfirmed = false;
        content.photoMatchConfirmed = false;
        content.displayApprovalBadge = false;
        return content;
    }

    public void verifyOwnership(Long artisanId, Long productOwnerId) {
        if (!artisanId.equals(productOwnerId)) {
            throw new ForbiddenException(ContentErrorMessage.FORBIDDEN.message());
        }
    }

    public void storeReactDocument(String reactDocumentJson) {
        this.reactDocument = reactDocumentJson;
    }

    public void submitForReview() {
        if (status != ContentStatus.DRAFT && status != ContentStatus.REJECTED) {
            throw new BusinessRuleViolationException(ContentErrorMessage.INVALID_STATUS_TRANSITION.message());
        }
        status = ContentStatus.PENDING_REVIEW;
    }

    public void approve(boolean factCheck, boolean photoMatch, boolean badge) {
        if (status != ContentStatus.PENDING_REVIEW) {
            throw new BusinessRuleViolationException(ContentErrorMessage.INVALID_STATUS_TRANSITION.message());
        }
        factCheckConfirmed = factCheck;
        photoMatchConfirmed = photoMatch;
        displayApprovalBadge = badge;
        status = ContentStatus.APPROVED;
    }

    public void reject() {
        if (status != ContentStatus.PENDING_REVIEW) {
            throw new BusinessRuleViolationException(ContentErrorMessage.INVALID_STATUS_TRANSITION.message());
        }
        status = ContentStatus.REJECTED;
    }

    public void publish() {
        if (status != ContentStatus.APPROVED) {
            throw new BusinessRuleViolationException(ContentErrorMessage.CONTENT_NOT_APPROVED.message());
        }
        status = ContentStatus.PUBLISHED;
    }

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
