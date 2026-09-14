package com.jangingmall.backend.content.application;

import com.jangingmall.backend.content.domain.BlockTag;

import java.util.List;

public sealed interface ContentCommand permits
    ContentCommand.BulkUpdate,
    ContentCommand.BlockUpdate {

    record BulkUpdate(
        Long productId,
        Long contentId,
        Long requesterId,
        List<BlockInput> blocks
    ) implements ContentCommand {}

    record BlockUpdate(
        Long productId,
        Long contentId,
        int blockOrder,
        Long requesterId,
        BlockTag tag,
        String text,
        String imageUrl
    ) implements ContentCommand {}

    record BlockInput(
        int order,
        BlockTag tag,
        String text,
        String imageUrl,
        String videoUrl
    ) {}
}
