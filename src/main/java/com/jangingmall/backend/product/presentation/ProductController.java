package com.jangingmall.backend.product.presentation;

import com.jangingmall.backend.global.common.response.ApiResponse;
import com.jangingmall.backend.product.application.ProductCommand;
import com.jangingmall.backend.product.application.ProductResponse;
import com.jangingmall.backend.product.application.ProductService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @PostMapping
    @PreAuthorize("hasRole('ARTISAN')")
    public ResponseEntity<ApiResponse<ProductResponse>> create(
        @AuthenticationPrincipal Long memberId,
        @Valid @RequestBody ProductRequest.Create request
    ) {
        ProductCommand.Create command = new ProductCommand.Create(
            memberId,
            request.categoryId(),
            request.subcategoryId(),
            request.title(),
            request.description(),
            request.price(),
            request.stock(),
            request.thumbnailUrl()
        );
        ProductResponse response = productService.create(command);
        return ResponseEntity.status(201).body(ApiResponse.created(response));
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('ARTISAN')")
    public ApiResponse<Page<ProductResponse>> myProducts(
        @AuthenticationPrincipal Long memberId,
        @PageableDefault(size = 20) Pageable pageable
    ) {
        return ApiResponse.ok(productService.findByArtisan(memberId, pageable));
    }

    @GetMapping
    public ApiResponse<Page<ProductResponse>> list(
        @PageableDefault(size = 20) Pageable pageable
    ) {
        return ApiResponse.ok(productService.findOnSale(pageable));
    }

    @GetMapping("/{productId}")
    public ApiResponse<ProductResponse> detail(@PathVariable Long productId) {
        return ApiResponse.ok(productService.findById(productId));
    }

    @PatchMapping("/{productId}")
    @PreAuthorize("hasRole('ARTISAN')")
    public ApiResponse<ProductResponse> update(
        @AuthenticationPrincipal Long memberId,
        @PathVariable Long productId,
        @Valid @RequestBody ProductRequest.Update request
    ) {
        ProductCommand.Update command = new ProductCommand.Update(
            productId,
            memberId,
            request.categoryId(),
            request.subcategoryId(),
            request.title(),
            request.description(),
            request.price(),
            request.stock(),
            request.thumbnailUrl()
        );
        return ApiResponse.ok(productService.update(command));
    }

    @PatchMapping("/{productId}/status")
    @PreAuthorize("hasRole('ARTISAN')")
    public ApiResponse<Void> changeStatus(
        @AuthenticationPrincipal Long memberId,
        @PathVariable Long productId,
        @Valid @RequestBody ProductRequest.ChangeStatus request
    ) {
        productService.changeStatus(new ProductCommand.ChangeStatus(productId, memberId, request.status()));
        return ApiResponse.noContent();
    }

    @DeleteMapping("/{productId}")
    @PreAuthorize("hasRole('ARTISAN')")
    public ApiResponse<Void> delete(
        @AuthenticationPrincipal Long memberId,
        @PathVariable Long productId
    ) {
        productService.delete(productId, memberId);
        return ApiResponse.noContent();
    }
}
