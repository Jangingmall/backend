package com.jangingmall.backend.product.application;

import java.util.List;

public sealed interface ProductReviewCommand {

    record Write(
        Long productId,
        Long writerId,
        Long orderItemId,
        short rating,
        String content,
        List<String> images
    ) implements ProductReviewCommand {
        public Write(Long productId, Long writerId, Long orderItemId, short rating, String content) {
            this(productId, writerId, orderItemId, rating, content, List.of());
        }
    }
}
