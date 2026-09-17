package com.jangingmall.backend.content.application;

public sealed interface ContentCommand permits
    ContentCommand.StoreReactDocument,
    ContentCommand.SubmitForReview,
    ContentCommand.Approve,
    ContentCommand.Reject,
    ContentCommand.Publish {

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
}
