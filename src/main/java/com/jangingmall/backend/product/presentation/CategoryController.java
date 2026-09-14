package com.jangingmall.backend.product.presentation;

import com.jangingmall.backend.global.common.response.ApiResponse;
import com.jangingmall.backend.product.application.CategoryQueryService;
import com.jangingmall.backend.product.application.CategoryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryQueryService categoryQueryService;

    @GetMapping("/categories")
    public ApiResponse<List<CategoryResponse.CategoryItem>> categories() {
        return ApiResponse.ok(categoryQueryService.findAllCategories());
    }

    @GetMapping("/categories/main")
    public ApiResponse<List<CategoryResponse.CategoryItem>> mainCategories() {
        return ApiResponse.ok(categoryQueryService.findAllCategories());
    }

    @GetMapping("/subcategories")
    public ApiResponse<List<CategoryResponse.SubcategoryItem>> subcategories() {
        return ApiResponse.ok(categoryQueryService.findAllSubcategories());
    }

    @GetMapping("/materials")
    public ApiResponse<List<String>> materials(
        @RequestParam(required = false) Long subcategoryId
    ) {
        return ApiResponse.ok(categoryQueryService.findMaterials(subcategoryId));
    }
}
