package com.jangingmall.backend.product.domain;

import java.util.List;

public interface ProductImageRepository {
    List<ProductImage> findByProductIdOrderByDisplayOrderAsc(Long productId);

    /** Used by list projections to avoid a query per order line. */
    List<ProductImage> findByProductIdInOrderByProductIdAscDisplayOrderAsc(List<Long> productIds);

    void deleteByProductId(Long productId);

    List<ProductImage> saveAll(Iterable<ProductImage> images);
}
