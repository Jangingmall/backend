package com.jangingmall.backend.product.application;

import com.jangingmall.backend.content.domain.AiContentClient;
import com.jangingmall.backend.content.domain.ContentBlockRepository;
import com.jangingmall.backend.content.domain.ContentRepository;
import com.jangingmall.backend.content.domain.AiProductUpdatePayload;
import com.jangingmall.backend.content.domain.Interview;
import com.jangingmall.backend.content.domain.InterviewRepository;
import com.jangingmall.backend.global.exception.DomainException;
import com.jangingmall.backend.global.exception.ErrorCode;
import com.jangingmall.backend.global.exception.NotFoundException;
import com.jangingmall.backend.image.application.ImageService;
import com.jangingmall.backend.image.domain.ImagePurpose;
import com.jangingmall.backend.product.domain.Category;
import com.jangingmall.backend.product.domain.CategoryRepository;
import com.jangingmall.backend.product.domain.Product;
import com.jangingmall.backend.product.domain.ProductErrorMessage;
import com.jangingmall.backend.product.domain.ProductImage;
import com.jangingmall.backend.product.domain.ProductImageRepository;
import com.jangingmall.backend.product.domain.ProductRepository;
import com.jangingmall.backend.product.domain.ProductStatus;
import com.jangingmall.backend.product.domain.Subcategory;
import com.jangingmall.backend.product.domain.SubcategoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final SubcategoryRepository subcategoryRepository;
    private final AiContentClient aiContentClient;
    private final InterviewRepository interviewRepository;
    private final ProductImageRepository productImageRepository;
    private final ImageService imageService;
    private final ContentRepository contentRepository;
    private final ContentBlockRepository contentBlockRepository;

    @Autowired
    public ProductService(ProductRepository productRepository, CategoryRepository categoryRepository,
                          SubcategoryRepository subcategoryRepository, AiContentClient aiContentClient,
                          InterviewRepository interviewRepository, ProductImageRepository productImageRepository,
                          ImageService imageService, ContentRepository contentRepository,
                          ContentBlockRepository contentBlockRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.subcategoryRepository = subcategoryRepository;
        this.aiContentClient = aiContentClient;
        this.interviewRepository = interviewRepository;
        this.productImageRepository = productImageRepository;
        this.imageService = imageService;
        this.contentRepository = contentRepository;
        this.contentBlockRepository = contentBlockRepository;
    }

    /** Backward-compatible constructor for callers that only manage legacy thumbnailUrl products. */
    public ProductService(ProductRepository productRepository, CategoryRepository categoryRepository,
                          SubcategoryRepository subcategoryRepository, AiContentClient aiContentClient,
                          InterviewRepository interviewRepository) {
        this(productRepository, categoryRepository, subcategoryRepository, aiContentClient, interviewRepository,
            null, null, null, null);
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
            command.thumbnailUrl(),
            command.giftThemes(),
            command.purposeTags(),
            command.productionPeriodDays(),
            command.colors()
        );
        Product saved = productRepository.save(product);
        replaceImages(saved, command.images(), command.artisanId(), true);
        return response(saved);
    }

    @Transactional(readOnly = true)
    public Page<ProductResponse> findByArtisan(Long artisanId, Pageable pageable) {
        return productRepository.findByArtisanId(artisanId, pageable).map(this::response);
    }

    @Transactional(readOnly = true)
    public Page<ProductResponse> findOnSale(Pageable pageable) {
        return productRepository.findOnSale(new ProductCommand.Search(null, null, null, null, null,
            null, null, false, null), pageable).map(this::response);
    }

    @Transactional(readOnly = true)
    public Page<ProductResponse> findOnSale(ProductCommand.Search search, Pageable pageable) {
        return productRepository.findOnSale(search, pageable).map(this::response);
    }

    @Transactional(readOnly = true)
    public ProductResponse findById(Long productId) {
        return response(getProduct(productId), true);
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
        replaceImages(product, command.images(), command.requesterId(), false);
        ProductResponse response = response(product);
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
        if (productImageRepository != null) {
            productImageRepository.deleteByProductId(productId);
        }
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

    private ProductResponse response(Product product) {
        return response(product, false);
    }

    private ProductResponse response(Product product, boolean includeContent) {
        if (productImageRepository == null) {
            return ProductResponse.from(product);
        }
        List<ProductResponse.ProductImageView> images = productImageRepository
            .findByProductIdOrderByDisplayOrderAsc(product.getId()).stream()
            .map(image -> new ProductResponse.ProductImageView(
                image.getImageId(), image.getAlt() == null || image.getAlt().isBlank() ? product.getTitle() : image.getAlt(),
                imageService == null ? List.of() : imageService.publicVariants(image.getImageId())
                    .stream().map(variant -> new ProductResponse.ImageVariantView(
                        variant.url(), variant.width(), variant.height(), variant.format())).toList()))
            .toList();
        List<ProductResponse.ContentBlockView> blocks = includeContent ? contentBlocks(product) : List.of();
        return ProductResponse.from(product, images, blocks);
    }

    private List<ProductResponse.ContentBlockView> contentBlocks(Product product) {
        if (contentRepository == null || contentBlockRepository == null) {
            return List.of();
        }
        return contentRepository.findByProductId(product.getId())
            .map(content -> contentBlockRepository.findByContentIdOrderByDisplayOrderAsc(content.getId()).stream()
                .map(block -> {
                    List<ProductResponse.ImageVariantView> variants = block.getImageId() == null || imageService == null
                        ? null
                        : imageService.publicVariants(block.getImageId()).stream()
                            .map(image -> new ProductResponse.ImageVariantView(image.url(), image.width(), image.height(), image.format()))
                            .toList();
                    return new ProductResponse.ContentBlockView(block.getDisplayOrder(), block.getTag(),
                        block.getImageId() != null, variants, block.getVideoUrl(), block.getText());
                }).toList())
            .orElseGet(List::of);
    }

    private void replaceImages(Product product, List<String> imageIds, Long requesterId, boolean creating) {
        if (productImageRepository == null || imageIds == null) {
            return;
        }
        List<String> normalized = imageIds.stream().filter(id -> id != null && !id.isBlank()).map(String::trim).toList();
        if (normalized.size() != imageIds.size()
            || normalized.stream().distinct().count() != normalized.size()) {
            throw new DomainException(ErrorCode.INVALID_INPUT);
        }
        if (imageService != null) {
            List<String> existing = creating ? List.of() : productImageRepository
                .findByProductIdOrderByDisplayOrderAsc(product.getId()).stream().map(ProductImage::getImageId).toList();
            List<String> newImages = normalized.stream().filter(id -> !existing.contains(id)).distinct().toList();
            if (!newImages.isEmpty()) {
                imageService.consumeOwned(requesterId, ImagePurpose.PRODUCT, newImages);
            }
        }
        productImageRepository.deleteByProductId(product.getId());
        if (!normalized.isEmpty()) {
            productImageRepository.saveAll(normalized.stream()
                .distinct()
                .map(id -> new ProductImage(product.getId(), id, normalized.indexOf(id), product.getTitle()))
                .toList());
        }
    }
}
