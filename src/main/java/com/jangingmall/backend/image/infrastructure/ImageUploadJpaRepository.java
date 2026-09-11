package com.jangingmall.backend.image.infrastructure;

import com.jangingmall.backend.image.domain.ImageUpload;
import com.jangingmall.backend.image.domain.ImageUploadRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ImageUploadJpaRepository extends JpaRepository<ImageUpload, String>, ImageUploadRepository {

    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from ImageUpload i where i.id = :imageId")
    Optional<ImageUpload> findByIdForUpdate(@Param("imageId") String imageId);

    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<ImageUpload> findTop100ByConsumedFalseAndExpiresAtLessThanEqualOrderByExpiresAtAsc(Instant now);

    @Override
    @Modifying(flushAutomatically = true)
    @Query("update ImageUpload i set i.consumed = true where i.id = :imageId and i.memberId = :memberId "
        + "and i.consumed = false and i.expiresAt > :now")
    int consumeIfOwnedAndActive(@Param("imageId") String imageId, @Param("memberId") Long memberId,
                                @Param("now") Instant now);
}
