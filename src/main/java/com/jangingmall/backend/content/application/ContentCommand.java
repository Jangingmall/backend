package com.jangingmall.backend.content.application;

import java.util.List;

public sealed interface ContentCommand permits
    ContentCommand.StoreReactDocument,
    ContentCommand.SubmitForReview,
    ContentCommand.Approve,
    ContentCommand.Reject,
    ContentCommand.Publish,
    ContentCommand.BulkUpdate,
    ContentCommand.BlockUpdate,
    ContentCommand.ReplaceBlocks,
    ContentCommand.UpdateBlock {

    record NodePatch(String nodeId, String text, String imageId) {}

    record StoreReactDocument(Long productId, String reactDocumentJson, Long requesterId) implements ContentCommand {}
    record SubmitForReview(Long productId, Long contentId, Long requesterId) implements ContentCommand {}
    record Approve(Long productId, Long contentId, Long requesterId, boolean factCheckConfirmed,
                   boolean photoMatchConfirmed, boolean displayApprovalBadge) implements ContentCommand {}
    record Reject(Long productId, Long contentId, Long requesterId) implements ContentCommand {}
    record Publish(Long productId, Long requesterId) implements ContentCommand {}

    record BulkUpdate(Long productId, Long contentId, Long requesterId, List<NodePatch> patches) implements ContentCommand {}
    record BlockUpdate(Long productId, Long contentId, String nodeId, Long requesterId, NodePatch patch) implements ContentCommand {}

    record ReplaceBlocks(Long productId, Long contentId, Long requesterId,
                         List<ContentBlockInput> blocks) implements ContentCommand {}
    record UpdateBlock(Long productId, Long contentId, int order, Long requesterId,
                       ContentBlockInput block) implements ContentCommand {}

    record ContentBlockInput(Integer order, String tag, Boolean hasImage, String imageUrl,
                             String videoUrl, String text) {}
}
