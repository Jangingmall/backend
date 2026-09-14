package com.jangingmall.backend.content.domain;

import java.util.Optional;

public interface ContentGenerationRepository {
    ContentGeneration save(ContentGeneration generation);
    Optional<ContentGeneration> findByIdAndProductId(Long id, Long productId);
}
