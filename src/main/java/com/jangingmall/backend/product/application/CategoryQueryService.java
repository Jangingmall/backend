package com.jangingmall.backend.product.application;

import com.jangingmall.backend.product.domain.CategoryRepository;
import com.jangingmall.backend.product.domain.SubcategoryMaterialRepository;
import com.jangingmall.backend.product.domain.SubcategoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CategoryQueryService {

    private final CategoryRepository categoryRepository;
    private final SubcategoryRepository subcategoryRepository;
    private final SubcategoryMaterialRepository subcategoryMaterialRepository;

    public CategoryQueryService(
        CategoryRepository categoryRepository,
        SubcategoryRepository subcategoryRepository,
        SubcategoryMaterialRepository subcategoryMaterialRepository
    ) {
        this.categoryRepository = categoryRepository;
        this.subcategoryRepository = subcategoryRepository;
        this.subcategoryMaterialRepository = subcategoryMaterialRepository;
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse.CategoryItem> findAllCategories() {
        return categoryRepository.findAll().stream()
            .map(CategoryResponse.CategoryItem::from)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse.SubcategoryItem> findAllSubcategories() {
        return subcategoryRepository.findAll().stream()
            .map(CategoryResponse.SubcategoryItem::from)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<String> findMaterials(Long subcategoryId) {
        if (subcategoryId == null) {
            return List.of();
        }
        return subcategoryMaterialRepository.findNamesBySubcategoryId(subcategoryId);
    }
}
