package com.jangingmall.backend.content.presentation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public sealed interface ContentRequest permits ContentRequest.Approve, ContentRequest.ReplaceBlocks,
    ContentRequest.UpdateBlock {

    record Approve(
        @NotNull Boolean factCheckConfirmed,
        @NotNull Boolean photoMatchConfirmed,
        @NotNull Boolean displayApprovalBadge
    ) implements ContentRequest {}

    record ReplaceBlocks(
        @NotNull @Size(min = 1, max = 100) List<@Valid Block> blocks
    ) implements ContentRequest {}

    record UpdateBlock(
        String tag,
        Boolean hasImage,
        String imageUrl,
        String videoUrl,
        String text
    ) implements ContentRequest {}

    record Block(
        @NotNull Integer order,
        @NotNull String tag,
        Boolean hasImage,
        String imageUrl,
        String videoUrl,
        String text
    ) {}
}
