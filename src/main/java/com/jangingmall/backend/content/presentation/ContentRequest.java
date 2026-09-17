package com.jangingmall.backend.content.presentation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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
        @NotEmpty @Valid List<NodePatchItem> patches
    ) implements ContentRequest {

        record NodePatchItem(
            @NotBlank String nodeId,
            String text,
            String imageId
        ) {}
    }

    record BlockUpdate(
        String text,
        String imageId
    ) implements ContentRequest {}
}
