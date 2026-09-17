package com.jangingmall.backend.product.domain;

import java.util.List;

public interface ProductImageRepository {
    List<ProductImage> findByProductIdOrderByDisplayOrderAsc(Long productId);

    void deleteByProductId(Long productId);

    List<ProductImage> saveAll(Iterable<ProductImage> images);
}
