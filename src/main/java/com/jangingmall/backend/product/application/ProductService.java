package com.jangingmall.backend.product.application;

import com.jangingmall.backend.global.exception.NotFoundException;
import com.jangingmall.backend.product.domain.Category;
import com.jangingmall.backend.product.domain.CategoryRepository;
import com.jangingmall.backend.product.domain.Product;
import com.jangingmall.backend.product.domain.ProductErrorMessage;
import com.jangingmall.backend.product.domain.ProductRepository;
import com.jangingmall.backend.product.domain.ProductStatus;
import com.jangingmall.backend.product.domain.Subcategory;
import com.jangingmall.backend.product.domain.SubcategoryRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final SubcategoryRepository subcategoryRepository;

    public ProductService(
        ProductRepository productRepository,
        CategoryRepository categoryRepository,
        SubcategoryRepository subcategoryRepository
    ) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.subcategoryRepository = subcategoryRepository;
    }

    @Transactional
    public ProductResponse create(ProductCommand.Create command) {
        Category category = resolveCategory(command.categoryId());
        Subcategory subcategory = resolveSubcategory(command.subcategoryId());
        Product product = Product.create(
            command.artisanId(),
            category,
            subcategory,
            command.title(),
            command.description(),
            command.price(),
            command.stock(),
            command.thumbnailUrl()
        );
        return ProductResponse.from(productRepository.save(product));
    }

    @Transactional(readOnly = true)
    public Page<ProductResponse> findByArtisan(Long artisanId, Pageable pageable) {
        return productRepository.findByArtisanId(artisanId, pageable).map(ProductResponse::from);
    }

    @Transactional(readOnly = true)
    public Page<ProductResponse> findOnSale(Pageable pageable) {
        return productRepository.findByStatus(ProductStatus.ON_SALE, pageable).map(ProductResponse::from);
    }

    @Transactional(readOnly = true)
    public ProductResponse findById(Long productId) {
        return ProductResponse.from(getProduct(productId));
    }

    @Transactional
    public ProductResponse update(ProductCommand.Update command) {
        Product product = getProduct(command.productId());
        Category category = resolveCategory(command.categoryId());
        Subcategory subcategory = resolveSubcategory(command.subcategoryId());
        product.update(
            category,
            subcategory,
            command.title(),
            command.description(),
            command.price(),
            command.stock(),
            command.thumbnailUrl(),
            command.requesterId()
        );
        return ProductResponse.from(product);
    }

    @Transactional
    public void changeStatus(ProductCommand.ChangeStatus command) {
        Product product = getProduct(command.productId());
        ProductStatus next = ProductStatus.valueOf(command.status());
        product.changeStatus(next, command.requesterId());
    }

    @Transactional
    public void delete(Long productId, Long requesterId) {
        Product product = getProduct(productId);
        product.verifyOwner(requesterId);
        productRepository.delete(product);
    }

    private Product getProduct(Long productId) {
        return productRepository.findById(productId)
            .orElseThrow(() -> new NotFoundException(ProductErrorMessage.NOT_FOUND.message()));
    }

    private Category resolveCategory(Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        return categoryRepository.findById(categoryId)
            .orElseThrow(() -> new NotFoundException("카테고리를 찾을 수 없습니다"));
    }

    private Subcategory resolveSubcategory(Long subcategoryId) {
        if (subcategoryId == null) {
            return null;
        }
        return subcategoryRepository.findById(subcategoryId)
            .orElseThrow(() -> new NotFoundException("서브카테고리를 찾을 수 없습니다"));
    }
}
