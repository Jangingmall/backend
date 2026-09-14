package com.jangingmall.backend.product.infrastructure;

import com.jangingmall.backend.product.domain.ProductReview;
import com.jangingmall.backend.product.domain.ProductReviewRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
interface JpaProductReviewRepositoryJpa extends JpaRepository<ProductReview, Long> {
    boolean existsByOrderItemId(Long orderItemId);
    Page<ProductReview> findByProductId(Long productId, Pageable pageable);
}

@Repository
class JpaProductReviewRepository implements ProductReviewRepository {

    private final JpaProductReviewRepositoryJpa jpa;

    JpaProductReviewRepository(JpaProductReviewRepositoryJpa jpa) {
        this.jpa = jpa;
    }

    @Override
    public ProductReview save(ProductReview review) {
        return jpa.save(review);
    }

    @Override
    public boolean existsByOrderItemId(Long orderItemId) {
        return jpa.existsByOrderItemId(orderItemId);
    }

    @Override
    public Page<ProductReview> findByProductId(Long productId, Pageable pageable) {
        return jpa.findByProductId(productId, pageable);
    }
}
