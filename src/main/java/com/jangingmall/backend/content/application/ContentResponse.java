package com.jangingmall.backend.content.application;

import com.jangingmall.backend.content.domain.Content;
import com.jangingmall.backend.content.domain.ContentEditHistory;
import com.jangingmall.backend.content.domain.ContentStatus;
import com.jangingmall.backend.content.domain.EditedByType;
import com.fasterxml.jackson.annotation.JsonRawValue;

import java.time.LocalDateTime;

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

    record BulkUpdated(
        Long contentId,
        Long productId,
        ContentStatus status,
        int version
    ) implements ContentResponse {}

    record BlockUpdated(
        Long contentId,
        int version,
        String nodeId
    ) implements ContentResponse {}
}
