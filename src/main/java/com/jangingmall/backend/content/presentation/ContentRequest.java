package com.jangingmall.backend.content.presentation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public sealed interface ContentRequest permits ContentRequest.Approve, ContentRequest.ReplaceBlocks,
    ContentRequest.UpdateBlock, ContentRequest.BulkUpdate, ContentRequest.BlockUpdate {

    record Approve(@NotNull Boolean factCheckConfirmed, @NotNull Boolean photoMatchConfirmed,
                   @NotNull Boolean displayApprovalBadge) implements ContentRequest {}
    record ReplaceBlocks(@NotNull @Size(min = 1, max = 100) List<@Valid Block> blocks) implements ContentRequest {}
    record UpdateBlock(String tag, Boolean hasImage, String imageUrl, String videoUrl, String text)
        implements ContentRequest {}
    record Block(@NotNull Integer order, @NotNull String tag, Boolean hasImage, String imageUrl,
                 String videoUrl, String text) {}

    record BulkUpdate(@NotEmpty @Valid List<NodePatchItem> patches) implements ContentRequest {
        record NodePatchItem(@NotBlank String nodeId, String text, String imageId) {}
    }
    record BlockUpdate(String text, String imageId) implements ContentRequest {}
}
