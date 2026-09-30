package com.jangingmall.backend.content.infrastructure;

import com.jangingmall.backend.content.domain.ContentGeneration;
import com.jangingmall.backend.content.domain.ContentGenerationRepository;
import com.jangingmall.backend.content.domain.GenerationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
interface JpaContentGenerationRepositoryJpa extends JpaRepository<ContentGeneration, Long> {
    Optional<ContentGeneration> findByIdAndProductId(Long id, Long productId);
    Optional<ContentGeneration> findByIdempotencyKey(String idempotencyKey);
    List<ContentGeneration> findAllByStatusAndRequestedAtBefore(GenerationStatus status, LocalDateTime deadline);
    List<ContentGeneration> findAllByStatus(GenerationStatus status);
    Optional<ContentGeneration> findFirstByProductIdAndStatusOrderByRequestedAtDesc(Long productId, GenerationStatus status);

    boolean existsByProductIdAndRequestedAtAfterAndStatusNot(Long productId, LocalDateTime requestedAt, GenerationStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from ContentGeneration g where g.id = :id")
    Optional<ContentGeneration> findByIdForUpdate(@Param("id") Long id);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update ContentGeneration g set g.renderClaimedAt = :now where g.id = :id and g.status = :status "
        + "and (g.renderClaimedAt is null or g.renderClaimedAt < :staleBefore)")
    int claimRender(@Param("id") Long id, @Param("status") GenerationStatus status,
                    @Param("now") LocalDateTime now, @Param("staleBefore") LocalDateTime staleBefore);
}

@Repository
class JpaContentGenerationRepository implements ContentGenerationRepository {

    private final JpaContentGenerationRepositoryJpa jpa;

    JpaContentGenerationRepository(JpaContentGenerationRepositoryJpa jpa) {
        this.jpa = jpa;
    }

    @Override
    public ContentGeneration save(ContentGeneration generation) {
        return jpa.save(generation);
    }

    @Override
    public Optional<ContentGeneration> findById(Long id) {
        return jpa.findById(id);
    }

    @Override
    public Optional<ContentGeneration> findByIdForUpdate(Long id) {
        return jpa.findByIdForUpdate(id);
    }

    @Override
    public boolean existsNewerNonFailed(Long productId, LocalDateTime requestedAfter) {
        return jpa.existsByProductIdAndRequestedAtAfterAndStatusNot(productId, requestedAfter, GenerationStatus.FAILED);
    }

    @Override
    @Transactional
    public boolean claimRender(Long id, LocalDateTime now, LocalDateTime staleBefore) {
        return jpa.claimRender(id, GenerationStatus.DRAFT_READY, now, staleBefore) > 0;
    }

    @Override
    public Optional<ContentGeneration> findByIdAndProductId(Long id, Long productId) {
        return jpa.findByIdAndProductId(id, productId);
    }

    @Override
    public Optional<ContentGeneration> findByIdempotencyKey(String idempotencyKey) {
        return jpa.findByIdempotencyKey(idempotencyKey);
    }

    @Override
    public List<ContentGeneration> findAllByStatusAndRequestedAtBefore(GenerationStatus status, LocalDateTime deadline) {
        return jpa.findAllByStatusAndRequestedAtBefore(status, deadline);
    }

    @Override
    public List<ContentGeneration> findAllByStatus(GenerationStatus status) {
        return jpa.findAllByStatus(status);
    }

    @Override
    public Optional<ContentGeneration> findFirstByProductIdAndStatusOrderByRequestedAtDesc(Long productId, GenerationStatus status) {
        return jpa.findFirstByProductIdAndStatusOrderByRequestedAtDesc(productId, status);
    }
}
