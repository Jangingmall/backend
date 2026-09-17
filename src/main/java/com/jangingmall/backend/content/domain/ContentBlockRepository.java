package com.jangingmall.backend.content.domain;

import java.util.List;
import java.util.Optional;

public interface ContentBlockRepository {
    List<ContentBlock> findByContentIdOrderByDisplayOrderAsc(Long contentId);

    Optional<ContentBlock> findByContentIdAndDisplayOrder(Long contentId, int displayOrder);

    void deleteByContentId(Long contentId);

    List<ContentBlock> saveAll(Iterable<ContentBlock> blocks);
}
