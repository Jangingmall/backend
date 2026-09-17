package com.jangingmall.backend.content.application;

import com.fasterxml.jackson.annotation.JsonRawValue;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.jangingmall.backend.content.domain.Content;
import com.jangingmall.backend.content.domain.ContentEditHistory;
import com.jangingmall.backend.content.domain.ContentStatus;
import com.jangingmall.backend.content.domain.EditedByType;
import java.util.List;

import java.time.LocalDateTime;

public sealed interface ContentResponse permits
    ContentResponse.Detail,
    ContentResponse.BlockChanged,
    ContentResponse.VersionHistory,
    ContentResponse.StatusChanged {

    record Detail(
        Long contentId,
        Long productId,
        ContentStatus status,
        int version,
        @JsonRawValue String reactDocument,
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        List<Block> blocks
    ) implements ContentResponse {

        public Detail(Long contentId, Long productId, ContentStatus status, int version, String reactDocument) {
            this(contentId, productId, status, version, reactDocument, List.of());
        }

        public static Detail from(Content content) {
            return from(content, List.of());
        }

        public static Detail from(Content content, List<Block> blocks) {
            return new Detail(
                content.getId(),
                content.getProductId(),
                content.getStatus(),
                content.getVersion(),
                content.getReactDocument(),
                blocks == null ? List.of() : List.copyOf(blocks)
            );
        }
    }

    record Block(
        int order,
        String tag,
        boolean hasImage,
        List<ImageVariant> imageVariants,
        String videoUrl,
        String text
    ) {}

    record ImageVariant(String url, int width, int height, String format) {}

    /** Response for CONTENT-012: one edited block and the resulting version. */
    record BlockChanged(
        Long contentId,
        int version,
        Block block
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

    record StatusChanged(
        Long contentId,
        ContentStatus status
    ) implements ContentResponse {

        public static StatusChanged from(Content content) {
            return new StatusChanged(content.getId(), content.getStatus());
        }
    }
}
