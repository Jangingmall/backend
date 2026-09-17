package com.jangingmall.backend.content.presentation;

import com.jangingmall.backend.content.domain.BlockTag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public sealed interface ContentRequest permits ContentRequest.Approve, ContentRequest.BulkUpdate, ContentRequest.BlockUpdate {

    record Approve(
        @NotNull Boolean factCheckConfirmed,
        @NotNull Boolean photoMatchConfirmed,
        @NotNull Boolean displayApprovalBadge
    ) implements ContentRequest {}

    record BulkUpdate(
        @NotEmpty @Valid List<BlockItem> blocks
    ) implements ContentRequest {

        record BlockItem(
            Integer order,
            BlockTag tag,
            Boolean hasImage,
            String imageUrl,
            String text
        ) {}
    }

    record BlockUpdate(
        BlockTag tag,
        String text,
        String imageUrl
    ) implements ContentRequest {}
}
