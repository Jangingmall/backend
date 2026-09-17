package com.jangingmall.backend.content.application;

import com.fasterxml.jackson.annotation.JsonRawValue;
import com.jangingmall.backend.content.domain.BlockTag;
import com.jangingmall.backend.content.domain.Content;
import com.jangingmall.backend.content.domain.ContentBlock;
import com.jangingmall.backend.content.domain.ContentEditHistory;
import com.jangingmall.backend.content.domain.ContentStatus;
import com.jangingmall.backend.content.domain.EditedByType;

import java.time.LocalDateTime;
import java.util.List;

public sealed interface ContentResponse permits
    ContentResponse.Detail,
    ContentResponse.VersionHistory,
    ContentResponse.StatusChanged,
    ContentResponse.BulkUpdated,
    ContentResponse.BlockUpdated {

    record Detail(
        Long contentId,
        Long productId,
        ContentStatus status,
        int version,
        @JsonRawValue String reactDocument
    ) implements ContentResponse {

        public static Detail from(Content content) {
            return new Detail(
                content.getId(),
                content.getProductId(),
                content.getStatus(),
                content.getVersion(),
                content.getReactDocument()
            );
        }
    }

    record VersionHistory(
        int version,
        LocalDateTime editedAt,
        EditedByType editedBy
    ) implements ContentResponse {

        public static VersionHistory from(ContentEditHistory history) {
            return new VersionHistory(history.getVersion(), history.getEditedAt(), history.getEditedByType());
        }
    }

    record StatusChanged(
        Long contentId,
        ContentStatus status
    ) implements ContentResponse {

        public static StatusChanged from(Content content) {
            return new StatusChanged(content.getId(), content.getStatus());
        }
    }

    record BlockView(
        int order,
        BlockTag tag,
        boolean hasImage,
        String imageUrl,
        String text
    ) {
        public static BlockView from(ContentBlock block) {
            return new BlockView(block.order(), block.tag(), block.hasImage(), block.imageUrl(), block.text());
        }
    }

    record BulkUpdated(
        Long contentId,
        Long productId,
        ContentStatus status,
        int version,
        List<BlockView> blocks
    ) implements ContentResponse {}

    record BlockUpdated(
        Long contentId,
        int version,
        BlockView block
    ) implements ContentResponse {}
}
