package com.jangingmall.backend.product.application;

import java.util.List;
import java.math.BigDecimal;

public sealed interface ProductReviewCommand {

    record Write(
        Long productId,
        Long writerId,
        Long orderItemId,
        BigDecimal rating,
        String content,
        List<String> images
    ) implements ProductReviewCommand {
        public Write(Long productId, Long writerId, Long orderItemId, short rating, String content) {
            this(productId, writerId, orderItemId, BigDecimal.valueOf(rating), content, List.of());
        }

        public Write(Long productId, Long writerId, Long orderItemId, short rating, String content, List<String> images) {
            this(productId, writerId, orderItemId, BigDecimal.valueOf(rating), content, images);
        }
    }
}
