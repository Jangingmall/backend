package com.jangingmall.backend.product.application;

import com.jangingmall.backend.product.domain.Category;
import com.jangingmall.backend.product.domain.Subcategory;

public sealed interface CategoryResponse {

    record CategoryItem(Long categoryId, String name) implements CategoryResponse {
        public static CategoryItem from(Category category) {
            return new CategoryItem(category.getId(), category.getName());
        }
    }

    record SubcategoryItem(Long subcategoryId, Long categoryId, String name) implements CategoryResponse {
        public static SubcategoryItem from(Subcategory subcategory) {
            return new SubcategoryItem(
                subcategory.getId(),
                subcategory.getCategory().getId(),
                subcategory.getName()
            );
        }
    }
}
