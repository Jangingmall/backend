package com.jangingmall.backend.product.domain;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository {

    List<Category> findAll();

    Optional<Category> findById(Long categoryId);
}
