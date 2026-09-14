package com.jangingmall.backend.product.application;

import com.jangingmall.backend.global.exception.BusinessRuleViolationException;
import com.jangingmall.backend.global.exception.NotFoundException;
import com.jangingmall.backend.product.domain.ProductErrorMessage;
import com.jangingmall.backend.product.domain.ProductRepository;
import com.jangingmall.backend.product.domain.ProductReview;
import com.jangingmall.backend.product.domain.ProductReviewErrorMessage;
import com.jangingmall.backend.product.domain.ProductReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductReviewService {

    private final ProductRepository productRepository;
    private final ProductReviewRepository reviewRepository;

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
        ProductReview review = ProductReview.write(
            command.productId(),
            command.writerId(),
            command.orderItemId(),
            command.rating(),
            command.content()
        );
        return ProductReviewResponse.ReviewView.from(reviewRepository.save(review));
    }
}
