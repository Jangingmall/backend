package com.jangingmall.backend.product.domain;

import java.util.Optional;

public interface ProductAnswerRepository {

    ProductAnswer save(ProductAnswer answer);

    Optional<ProductAnswer> findByQuestionId(Long questionId);

    boolean existsByQuestionId(Long questionId);
}
