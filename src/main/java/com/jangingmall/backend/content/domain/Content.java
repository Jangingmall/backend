package com.jangingmall.backend.content.domain;

import com.jangingmall.backend.global.exception.ForbiddenException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "content", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder ASC")
    private List<ContentBlock> blocks = new ArrayList<>();

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

    public void replaceBlocks(List<ContentBlock> newBlocks) {
        this.blocks.clear();
        this.blocks.addAll(newBlocks);
    }

    public void touchVersion() {
        // @Version 필드만으로는 자식 엔티티 변경 시 부모 버전이 증가하지 않으므로
        // 블록 단건 수정 시 명시적으로 호출해 version을 강제 증가시킨다
        this.version++;
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
