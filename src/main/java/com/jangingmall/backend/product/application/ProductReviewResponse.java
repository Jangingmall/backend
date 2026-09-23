package com.jangingmall.backend.product.application;

import com.jangingmall.backend.product.domain.ProductReview;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.List;
import tools.jackson.databind.ObjectMapper;

public sealed interface ProductReviewResponse {

    record ReviewView(
        Long reviewId,
        Long productId,
        Long writerId,
        Long orderItemId,
        BigDecimal rating,
        String content,
        List<String> images,
        LocalDateTime createdAt
    ) implements ProductReviewResponse {

        private static final ObjectMapper JSON = new ObjectMapper();

        public ReviewView(Long reviewId, Long productId, Long writerId, Long orderItemId, BigDecimal rating,
                           String content, LocalDateTime createdAt) {
            this(reviewId, productId, writerId, orderItemId, rating, content, parseImages("[]"), createdAt);
        }

        public ReviewView(Long reviewId, Long productId, Long writerId, Long orderItemId, short rating,
                          String content, LocalDateTime createdAt) {
            this(reviewId, productId, writerId, orderItemId, BigDecimal.valueOf(rating), content, createdAt);
        }

        public ReviewView(Long reviewId, Long productId, Long writerId, Long orderItemId, short rating,
                          String content, List<String> images, LocalDateTime createdAt) {
            this(reviewId, productId, writerId, orderItemId, BigDecimal.valueOf(rating), content, images, createdAt);
        }

        public static ReviewView from(ProductReview review) {
            return new ReviewView(
                review.getId(),
                review.getProductId(),
                review.getWriterId(),
                review.getOrderItemId(),
                review.getRating(),
                review.getContent(),
                parseImages(review.getImages()),
                review.getCreatedAt()
            );
        }

        private static List<String> parseImages(String json) {
            if (json == null || json.isBlank()) {
                return List.of();
            }
            try {
                return JSON.readValue(json, List.class).stream().map(String::valueOf).toList();
            } catch (Exception ignored) {
                return List.of();
            }
        }
    }
}
