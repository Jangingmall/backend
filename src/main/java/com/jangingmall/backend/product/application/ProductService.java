package com.jangingmall.backend.product.application;

import com.jangingmall.backend.content.domain.AiContentClient;
import com.jangingmall.backend.content.domain.AiProductUpdatePayload;
import com.jangingmall.backend.content.domain.Interview;
import com.jangingmall.backend.content.domain.InterviewRepository;
import com.jangingmall.backend.global.exception.NotFoundException;
import com.jangingmall.backend.product.domain.Category;
import com.jangingmall.backend.product.domain.CategoryRepository;
import com.jangingmall.backend.product.domain.Product;
import com.jangingmall.backend.product.domain.ProductErrorMessage;
import com.jangingmall.backend.product.domain.ProductRepository;
import com.jangingmall.backend.product.domain.ProductStatus;
import com.jangingmall.backend.product.domain.Subcategory;
import com.jangingmall.backend.product.domain.SubcategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final SubcategoryRepository subcategoryRepository;
    private final AiContentClient aiContentClient;
    private final InterviewRepository interviewRepository;

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
            command.thumbnailUrl(),
            command.giftThemes(),
            command.purposeTags(),
            command.productionPeriodDays(),
            command.colors()
        );
        return ProductResponse.from(productRepository.save(product));
    }

    @Transactional(readOnly = true)
    public Page<ProductResponse> findByArtisan(Long artisanId, Pageable pageable) {
        return productRepository.findByArtisanId(artisanId, pageable).map(ProductResponse::from);
    }

    @Transactional(readOnly = true)
    public Page<ProductResponse> findOnSale(ProductCommand.Search search, Pageable pageable) {
        return productRepository.findOnSale(search, pageable).map(ProductResponse::from);
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
            command.giftThemes(),
            command.purposeTags(),
            command.productionPeriodDays(),
            command.colors(),
            command.requesterId()
        );
        ProductResponse response = ProductResponse.from(product);
        notifyAiProductUpdated(command.productId(), product);
        return response;
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
        aiContentClient.deleteProduct(productId);
        productRepository.delete(product);
    }

    private void notifyAiProductUpdated(Long productId, Product product) {
        try {
            Optional<Interview> interview = interviewRepository.findByProductId(productId);
            String makingStory = interview.map(Interview::getProcess).orElse("");
            String usageCare = interview.map(Interview::getMaterials).orElse("");
            String categoryName = product.getCategory() != null ? product.getCategory().getName() : null;
            AiProductUpdatePayload payload = new AiProductUpdatePayload(
                new AiProductUpdatePayload.ProductPatch(
                    product.getTitle(), categoryName, product.getMaterial(), product.getPrice(),
                    product.getGiftThemes(), product.getPurposeTags(),
                    makingStory, usageCare, product.getProductionPeriodDays(), product.getColors()
                )
            );
            aiContentClient.updateProduct(productId, payload);
        } catch (Exception e) {
            log.error("AI 상품 수정 동기화 실패 productId={} reason={}", productId, e.getMessage());
        }
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
