package com.jangingmall.backend.content.infrastructure;

import com.jangingmall.backend.content.domain.ContentGeneration;
import com.jangingmall.backend.content.domain.ContentGenerationRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
interface JpaContentGenerationRepositoryJpa extends JpaRepository<ContentGeneration, Long> {
    Optional<ContentGeneration> findByIdAndProductId(Long id, Long productId);
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
}
