package com.jangingmall.backend.content.presentation;

import com.jangingmall.backend.content.domain.BlockTag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public sealed interface ContentRequest permits ContentRequest.BulkUpdate, ContentRequest.BlockUpdate {

    record BulkUpdate(
        @NotEmpty @Valid List<BlockInput> blocks
    ) implements ContentRequest {}

    record BlockUpdate(
        BlockTag tag,
        String text,
        String imageUrl
    ) implements ContentRequest {}

    record BlockInput(
        @NotNull Integer order,
        @NotNull BlockTag tag,
        String text,
        String imageUrl,
        String videoUrl
    ) {}
}
