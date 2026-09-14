package com.jangingmall.backend.product.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface ProductRepository {

    Product save(Product product);

    Optional<Product> findById(Long productId);

    Page<Product> findByArtisanId(Long artisanId, Pageable pageable);

    Page<Product> findByStatus(ProductStatus status, Pageable pageable);

    void delete(Product product);
}
