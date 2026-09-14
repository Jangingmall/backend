package com.jangingmall.backend.product.domain;

import com.jangingmall.backend.global.exception.BusinessRuleViolationException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "product_review", uniqueConstraints = @UniqueConstraint(columnNames = "order_item_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "review_id")
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "writer_id", nullable = false)
    private Long writerId;

    @Column(name = "order_item_id", nullable = false)
    private Long orderItemId;

    @Column(nullable = false)
    private short rating;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static ProductReview write(Long productId, Long writerId, Long orderItemId, short rating, String content) {
        if (rating < 1 || rating > 5) {
            throw new BusinessRuleViolationException(ProductReviewErrorMessage.INVALID_RATING.message());
        }
        ProductReview r = new ProductReview();
        r.productId = productId;
        r.writerId = writerId;
        r.orderItemId = orderItemId;
        r.rating = rating;
        r.content = content;
        return r;
    }

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
