package com.jangingmall.backend.content.infrastructure;

import com.jangingmall.backend.content.domain.ContentGeneration;
import com.jangingmall.backend.content.domain.ContentGenerationRepository;
import com.jangingmall.backend.content.domain.GenerationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
interface JpaContentGenerationRepositoryJpa extends JpaRepository<ContentGeneration, Long> {
    Optional<ContentGeneration> findByIdAndProductId(Long id, Long productId);
    Optional<ContentGeneration> findByIdempotencyKey(String idempotencyKey);
    List<ContentGeneration> findAllByStatusAndRequestedAtBefore(GenerationStatus status, LocalDateTime deadline);
    Optional<ContentGeneration> findFirstByProductIdAndStatusOrderByRequestedAtDesc(Long productId, GenerationStatus status);
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
    public Optional<ContentGeneration> findFirstByProductIdAndStatusOrderByRequestedAtDesc(Long productId, GenerationStatus status) {
        return jpa.findFirstByProductIdAndStatusOrderByRequestedAtDesc(productId, status);
    }
}
