package com.jangingmall.backend.content.application;

import java.util.List;

public sealed interface GenerationCommand permits GenerationCommand.Request {

    record Request(
        Long productId,
        Long requesterId,
        List<String> images,
        String productName,
        String howMade,
        String careTips
    ) implements GenerationCommand {}
}
