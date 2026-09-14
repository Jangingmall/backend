package com.jangingmall.backend.content.domain;

import java.util.Optional;

public interface ContentRepository {

    Content save(Content content);

    Optional<Content> findByProductId(Long productId);

    Optional<Content> findByIdAndProductId(Long contentId, Long productId);
}
