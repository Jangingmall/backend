package com.jangingmall.backend.product.application;

public sealed interface ProductReviewCommand {

    record Write(
        Long productId,
        Long writerId,
        Long orderItemId,
        short rating,
        String content
    ) implements ProductReviewCommand {}
}
