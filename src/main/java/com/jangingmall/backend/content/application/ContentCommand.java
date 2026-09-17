package com.jangingmall.backend.content.application;

import com.jangingmall.backend.content.domain.ContentBlock;

import java.util.List;

public sealed interface ContentCommand permits
    ContentCommand.StoreReactDocument,
    ContentCommand.SubmitForReview,
    ContentCommand.Approve,
    ContentCommand.Reject,
    ContentCommand.Publish,
    ContentCommand.BulkUpdate,
    ContentCommand.BlockUpdate {

    record StoreReactDocument(
        Long productId,
        String reactDocumentJson,
        Long requesterId
    ) implements ContentCommand {}

    record SubmitForReview(
        Long productId,
        Long contentId,
        Long requesterId
    ) implements ContentCommand {}

    record Approve(
        Long productId,
        Long contentId,
        Long requesterId,
        boolean factCheckConfirmed,
        boolean photoMatchConfirmed,
        boolean displayApprovalBadge
    ) implements ContentCommand {}

    record Reject(
        Long productId,
        Long contentId,
        Long requesterId
    ) implements ContentCommand {}

    record Publish(
        Long productId,
        Long requesterId
    ) implements ContentCommand {}

    record BulkUpdate(
        Long productId,
        Long contentId,
        Long requesterId,
        List<ContentBlock> blocks
    ) implements ContentCommand {}

    record BlockUpdate(
        Long productId,
        Long contentId,
        int blockOrder,
        Long requesterId,
        ContentBlock block
    ) implements ContentCommand {}
}
