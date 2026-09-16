package com.jangingmall.backend.image.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ImageUploadRepository {
    ImageUpload save(ImageUpload imageUpload);
    Optional<ImageUpload> findById(String imageId);
    Optional<ImageUpload> findByIdForUpdate(String imageId);
    List<ImageUpload> findTop100ByConsumedFalseAndExpiresAtLessThanEqualOrderByExpiresAtAsc(Instant now);
    void delete(ImageUpload imageUpload);
    int consumeIfOwnedAndActive(String imageId, Long memberId, Instant now);
}
