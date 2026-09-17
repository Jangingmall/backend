package com.jangingmall.backend.product.domain;

import com.jangingmall.backend.product.application.ProductCommand;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface ProductRepository {

    Product save(Product product);

    Optional<Product> findById(Long productId);

    Page<Product> findByArtisanId(Long artisanId, Pageable pageable);

    Page<Product> findOnSale(ProductCommand.Search search, Pageable pageable);

    void delete(Product product);
}
