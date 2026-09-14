package com.jangingmall.backend.product.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "product_question")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductQuestion {

    private static final String SECRET_CONTENT = "비공개 문의입니다.";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "question_id")
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "writer_id", nullable = false)
    private Long writerId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "is_secret", nullable = false)
    private boolean secret;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static ProductQuestion ask(Long productId, Long writerId, String content, boolean secret) {
        ProductQuestion q = new ProductQuestion();
        q.productId = productId;
        q.writerId = writerId;
        q.content = content;
        q.secret = secret;
        return q;
    }

    public String visibleContent(Long viewerId, Long artisanId) {
        if (!secret) {
            return content;
        }
        if (writerId.equals(viewerId) || artisanId.equals(viewerId)) {
            return content;
        }
        return SECRET_CONTENT;
    }

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
