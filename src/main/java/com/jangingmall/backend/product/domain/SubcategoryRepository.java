package com.jangingmall.backend.product.domain;

import java.util.List;
import java.util.Optional;

public interface SubcategoryRepository {

    List<Subcategory> findAll();

    List<Subcategory> findByCategoryId(Long categoryId);

    Optional<Subcategory> findById(Long subcategoryId);
}
