package com.jangingmall.backend.product.application;

import com.jangingmall.backend.product.domain.ProductReview;

import java.time.LocalDateTime;

public sealed interface ProductReviewResponse {

    record ReviewView(
        Long reviewId,
        Long productId,
        Long writerId,
        Long orderItemId,
        short rating,
        String content,
        LocalDateTime createdAt
    ) implements ProductReviewResponse {

        public static ReviewView from(ProductReview review) {
            return new ReviewView(
                review.getId(),
                review.getProductId(),
                review.getWriterId(),
                review.getOrderItemId(),
                review.getRating(),
                review.getContent(),
                review.getCreatedAt()
            );
        }
    }
}
