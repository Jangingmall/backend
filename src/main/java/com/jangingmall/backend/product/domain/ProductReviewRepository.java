package com.jangingmall.backend.product.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ProductReviewRepository {

    ProductReview save(ProductReview review);

    boolean existsByOrderItemId(Long orderItemId);

    Page<ProductReview> findByProductId(Long productId, Pageable pageable);
}
