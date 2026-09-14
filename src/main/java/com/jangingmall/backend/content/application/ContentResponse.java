package com.jangingmall.backend.content.application;

import com.jangingmall.backend.content.domain.Content;
import com.jangingmall.backend.content.domain.ContentBlock;
import com.jangingmall.backend.content.domain.ContentEditHistory;
import com.jangingmall.backend.content.domain.ContentStatus;
import com.jangingmall.backend.content.domain.EditedByType;

import java.time.LocalDateTime;
import java.util.List;

public sealed interface ContentResponse permits
    ContentResponse.Detail,
    ContentResponse.BlockEdit,
    ContentResponse.VersionHistory {

    record Detail(
        Long contentId,
        Long productId,
        ContentStatus status,
        int version,
        List<BlockView> blocks
    ) implements ContentResponse {

        public static Detail from(Content content) {
            List<BlockView> blockViews = content.getBlocks().stream()
                .map(BlockView::from)
                .toList();
            return new Detail(content.getId(), content.getProductId(), content.getStatus(), content.getVersion(), blockViews);
        }
    }

    record BlockEdit(
        Long contentId,
        int version,
        BlockView block
    ) implements ContentResponse {}

    record VersionHistory(
        int version,
        LocalDateTime editedAt,
        EditedByType editedBy
    ) implements ContentResponse {

        public static VersionHistory from(ContentEditHistory history) {
            return new VersionHistory(history.getVersion(), history.getEditedAt(), history.getEditedByType());
        }
    }

    record BlockView(
        int order,
        String tag,
        boolean hasImage,
        String imageId,
        String videoUrl,
        String text
    ) {
        public static BlockView from(ContentBlock block) {
            return new BlockView(
                block.getDisplayOrder(),
                block.getTag().name(),
                block.getImageId() != null,
                block.getImageId(),
                block.getVideoUrl(),
                block.getText()
            );
        }
    }
}
