package com.jangingmall.backend.content.domain;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ContentGenerationRepository {
    ContentGeneration save(ContentGeneration generation);
    Optional<ContentGeneration> findById(Long id);
    Optional<ContentGeneration> findByIdAndProductId(Long id, Long productId);
    Optional<ContentGeneration> findByIdempotencyKey(String idempotencyKey);
    List<ContentGeneration> findAllByStatusAndRequestedAtBefore(GenerationStatus status, LocalDateTime deadline);
}
