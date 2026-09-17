package com.jangingmall.backend.product.application;

import com.jangingmall.backend.global.exception.BusinessRuleViolationException;
import com.jangingmall.backend.global.exception.NotFoundException;
import com.jangingmall.backend.global.exception.DomainException;
import com.jangingmall.backend.global.exception.ErrorCode;
import com.jangingmall.backend.image.application.ImageService;
import com.jangingmall.backend.image.domain.ImagePurpose;
import com.jangingmall.backend.product.domain.ProductErrorMessage;
import com.jangingmall.backend.product.domain.ProductRepository;
import com.jangingmall.backend.product.domain.ProductReview;
import com.jangingmall.backend.product.domain.ProductReviewErrorMessage;
import com.jangingmall.backend.product.domain.ProductReviewRepository;
import java.util.List;
import java.util.Objects;
import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductReviewService {

    private final ProductRepository productRepository;
    private final ProductReviewRepository reviewRepository;
    private final ImageService images;
    private final ObjectMapper objectMapper;

    @Autowired
    public ProductReviewService(ProductRepository productRepository, ProductReviewRepository reviewRepository,
                                ImageService images, ObjectMapper objectMapper) {
        this.productRepository = productRepository;
        this.reviewRepository = reviewRepository;
        this.images = images;
        this.objectMapper = objectMapper;
    }

    public ProductReviewService(ProductRepository productRepository, ProductReviewRepository reviewRepository) {
        this(productRepository, reviewRepository, null, null);
    }

    @Transactional(readOnly = true)
    public Page<ProductReviewResponse.ReviewView> findReviews(Long productId, Pageable pageable) {
        if (!productRepository.findById(productId).isPresent()) {
            throw new NotFoundException(ProductErrorMessage.NOT_FOUND.message());
        }
        return reviewRepository.findByProductId(productId, pageable)
            .map(ProductReviewResponse.ReviewView::from);
    }

    @Transactional
    public ProductReviewResponse.ReviewView write(ProductReviewCommand.Write command) {
        if (!productRepository.findById(command.productId()).isPresent()) {
            throw new NotFoundException(ProductErrorMessage.NOT_FOUND.message());
        }
        if (reviewRepository.existsByOrderItemId(command.orderItemId())) {
            throw new BusinessRuleViolationException(ProductReviewErrorMessage.ALREADY_REVIEWED.message());
        }
        List<String> imageIds = command.images() == null ? List.of() : command.images().stream()
            .filter(Objects::nonNull).map(String::trim).toList();
        if (imageIds.size() > 5 || imageIds.stream().anyMatch(String::isBlank)
            || imageIds.stream().distinct().count() != imageIds.size()) {
            throw new DomainException(ErrorCode.INVALID_INPUT);
        }
        if (!imageIds.isEmpty()) {
            if (images == null) {
                throw new DomainException(ErrorCode.INVALID_INPUT);
            }
            images.consumeOwned(command.writerId(), ImagePurpose.PRODUCT, imageIds);
        }
        String imagesJson = serializeImages(imageIds);
        ProductReview review = ProductReview.write(
            command.productId(),
            command.writerId(),
            command.orderItemId(),
            command.rating(),
            command.content(),
            imagesJson
        );
        return ProductReviewResponse.ReviewView.from(reviewRepository.save(review));
    }

    private String serializeImages(List<String> imageIds) {
        if (objectMapper == null) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(imageIds);
        } catch (Exception exception) {
            throw new IllegalStateException("후기 이미지를 저장할 수 없습니다.", exception);
        }
    }
}
