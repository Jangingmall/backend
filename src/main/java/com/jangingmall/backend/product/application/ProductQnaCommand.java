package com.jangingmall.backend.product.application;

public sealed interface ProductQnaCommand {

    record Ask(
        Long productId,
        Long writerId,
        String content,
        boolean secret
    ) implements ProductQnaCommand {}

    record Answer(
        Long questionId,
        Long artisanId,
        String content
    ) implements ProductQnaCommand {}
}
