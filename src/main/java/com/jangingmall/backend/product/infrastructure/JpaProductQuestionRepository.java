package com.jangingmall.backend.product.infrastructure;

import com.jangingmall.backend.product.domain.ProductQuestion;
import com.jangingmall.backend.product.domain.ProductQuestionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
interface JpaProductQuestionRepositoryJpa extends JpaRepository<ProductQuestion, Long> {
    Page<ProductQuestion> findByProductId(Long productId, Pageable pageable);
}

@Repository
class JpaProductQuestionRepository implements ProductQuestionRepository {

    private final JpaProductQuestionRepositoryJpa jpa;

    JpaProductQuestionRepository(JpaProductQuestionRepositoryJpa jpa) {
        this.jpa = jpa;
    }

    @Override
    public ProductQuestion save(ProductQuestion question) {
        return jpa.save(question);
    }

    @Override
    public Optional<ProductQuestion> findById(Long questionId) {
        return jpa.findById(questionId);
    }

    @Override
    public Page<ProductQuestion> findByProductId(Long productId, Pageable pageable) {
        return jpa.findByProductId(productId, pageable);
    }
}
