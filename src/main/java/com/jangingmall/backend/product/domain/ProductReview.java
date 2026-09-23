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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.math.BigDecimal;

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

    @Column(nullable = false, precision = 2, scale = 1)
    private BigDecimal rating;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    /** Consumed public image group IDs, serialized as a JSON array. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String images = "[]";

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static ProductReview write(Long productId, Long writerId, Long orderItemId, BigDecimal rating, String content) {
        return write(productId, writerId, orderItemId, rating, content, "[]");
    }

    public static ProductReview write(Long productId, Long writerId, Long orderItemId, BigDecimal rating, String content,
                                      String images) {
        if (!isHalfPointRating(rating)) {
            throw new BusinessRuleViolationException(ProductReviewErrorMessage.INVALID_RATING.message());
        }
        ProductReview r = new ProductReview();
        r.productId = productId;
        r.writerId = writerId;
        r.orderItemId = orderItemId;
        r.rating = rating;
        r.content = content;
        r.images = images == null ? "[]" : images;
        return r;
    }

    /** Compatibility overload for callers compiled against the former integer-only contract. */
    public static ProductReview write(Long productId, Long writerId, Long orderItemId, short rating, String content) {
        return write(productId, writerId, orderItemId, BigDecimal.valueOf(rating), content);
    }

    public static ProductReview write(Long productId, Long writerId, Long orderItemId, short rating, String content,
                                      String images) {
        return write(productId, writerId, orderItemId, BigDecimal.valueOf(rating), content, images);
    }

    private static boolean isHalfPointRating(BigDecimal rating) {
        if (rating == null || rating.compareTo(BigDecimal.ONE) < 0 || rating.compareTo(BigDecimal.valueOf(5)) > 0) {
            return false;
        }
        return rating.multiply(BigDecimal.valueOf(2)).stripTrailingZeros().scale() <= 0;
    }

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
