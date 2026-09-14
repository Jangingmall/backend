package com.jangingmall.backend.content.domain;

import java.util.Optional;

public interface ContentBlockRepository {

    ContentBlock save(ContentBlock block);

    Optional<ContentBlock> findByContentIdAndDisplayOrder(Long contentId, short displayOrder);

    void deleteAllByContentId(Long contentId);
}
