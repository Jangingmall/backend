package com.jangingmall.backend.product.infrastructure;

import com.jangingmall.backend.product.domain.ProductAnswer;
import com.jangingmall.backend.product.domain.ProductAnswerRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
interface JpaProductAnswerRepositoryJpa extends JpaRepository<ProductAnswer, Long> {
    Optional<ProductAnswer> findByQuestionId(Long questionId);
    boolean existsByQuestionId(Long questionId);
}

@Repository
class JpaProductAnswerRepository implements ProductAnswerRepository {

    private final JpaProductAnswerRepositoryJpa jpa;

    JpaProductAnswerRepository(JpaProductAnswerRepositoryJpa jpa) {
        this.jpa = jpa;
    }

    @Override
    public ProductAnswer save(ProductAnswer answer) {
        return jpa.save(answer);
    }

    @Override
    public Optional<ProductAnswer> findByQuestionId(Long questionId) {
        return jpa.findByQuestionId(questionId);
    }

    @Override
    public boolean existsByQuestionId(Long questionId) {
        return jpa.existsByQuestionId(questionId);
    }
}
