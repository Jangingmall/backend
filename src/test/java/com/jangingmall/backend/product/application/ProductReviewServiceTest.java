package com.jangingmall.backend.product.application;

import com.jangingmall.backend.global.exception.BusinessRuleViolationException;
import com.jangingmall.backend.global.exception.NotFoundException;
import com.jangingmall.backend.image.application.ImageService;
import com.jangingmall.backend.product.domain.Product;
import com.jangingmall.backend.product.domain.ProductRepository;
import com.jangingmall.backend.product.domain.ProductReview;
import com.jangingmall.backend.product.domain.ProductReviewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductReviewServiceTest {

    @Mock
    private ProductRepository productRepository;
    @Mock
    private ProductReviewRepository reviewRepository;
    @Mock
    private ImageService images;

    private ProductReviewService service;

    private Product product;

    @BeforeEach
    void setUp() {
        service = new ProductReviewService(productRepository, reviewRepository, images, new ObjectMapper());
        product = Product.create(10L, null, null, "청자 다완", "설명", 85000, 10, null);
        ReflectionTestUtils.setField(product, "id", 1L);
    }

    @Test
    @DisplayName("후기 목록을 페이징으로 조회한다")
    void findReviews_returnsPage() {
        ProductReview review = ProductReview.write(1L, 99L, 100L, (short) 5, "좋아요");
        ReflectionTestUtils.setField(review, "id", 1L);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(reviewRepository.findByProductId(anyLong(), any())).thenReturn(new PageImpl<>(List.of(review)));

        Page<ProductReviewResponse.ReviewView> result = service.findReviews(1L, PageRequest.of(0, 20));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).rating()).isEqualTo((short) 5);
    }

    @Test
    @DisplayName("존재하지 않는 상품에 후기를 조회하면 NotFoundException이 발생한다")
    void findReviews_productNotFound() {
        when(productRepository.findById(anyLong())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findReviews(99L, PageRequest.of(0, 20)))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("후기를 정상 등록한다")
    void write_success() {
        ProductReview review = ProductReview.write(1L, 99L, 100L, (short) 4, "만족합니다");
        ReflectionTestUtils.setField(review, "id", 1L);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(reviewRepository.existsByOrderItemId(100L)).thenReturn(false);
        when(reviewRepository.save(any())).thenReturn(review);

        ProductReviewResponse.ReviewView result = service.write(
            new ProductReviewCommand.Write(1L, 99L, 100L, (short) 4, "만족합니다")
        );

        assertThat(result.reviewId()).isEqualTo(1L);
        assertThat(result.rating()).isEqualTo((short) 4);
    }

    @Test
    @DisplayName("후기 작성 시 첨부 이미지의 소유권을 확인하고 이미지 ID를 저장한다")
    void write_withImages() {
        ProductReview review = ProductReview.write(1L, 99L, 100L, (short) 5, "사진 후기", "[\"image-1\"]");
        ReflectionTestUtils.setField(review, "id", 2L);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(reviewRepository.existsByOrderItemId(100L)).thenReturn(false);
        when(images.consumeOwned(99L, com.jangingmall.backend.image.domain.ImagePurpose.PRODUCT,
            List.of("image-1"))).thenReturn(List.of("image-1"));
        when(reviewRepository.save(any())).thenReturn(review);

        ProductReviewResponse.ReviewView result = service.write(new ProductReviewCommand.Write(
            1L, 99L, 100L, (short) 5, "사진 후기", List.of("image-1")));

        assertThat(result.images()).containsExactly("image-1");
    }

    @Test
    @DisplayName("동일 주문 항목에 중복 후기를 등록하면 BusinessRuleViolationException이 발생한다")
    void write_alreadyReviewed() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(reviewRepository.existsByOrderItemId(100L)).thenReturn(true);

        assertThatThrownBy(() -> service.write(new ProductReviewCommand.Write(1L, 99L, 100L, (short) 5, "후기")))
            .isInstanceOf(BusinessRuleViolationException.class)
            .hasMessageContaining("이미 후기");
    }
}
