package com.jangingmall.backend.product.domain;

import java.util.List;

public interface SubcategoryMaterialRepository {

    List<String> findNamesBySubcategoryId(Long subcategoryId);
}
