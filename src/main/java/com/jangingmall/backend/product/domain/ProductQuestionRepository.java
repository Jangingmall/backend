package com.jangingmall.backend.product.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface ProductQuestionRepository {

    ProductQuestion save(ProductQuestion question);

    Optional<ProductQuestion> findById(Long questionId);

    Page<ProductQuestion> findByProductId(Long productId, Pageable pageable);
}
