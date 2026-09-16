package com.jangingmall.backend.content.application;

import java.util.List;

public sealed interface GenerationCommand permits GenerationCommand.Request, GenerationCommand.Complete {

    record Request(
        Long productId,
        Long requesterId,
        List<String> images,
        String productName,
        String howMade,
        String careTips
    ) implements GenerationCommand {}

    record Complete(
        Long generationId,
        String blocksJson
    ) implements GenerationCommand {}
}
