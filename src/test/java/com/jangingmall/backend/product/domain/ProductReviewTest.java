package com.jangingmall.backend.product.domain;

import com.jangingmall.backend.global.exception.BusinessRuleViolationException;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductReviewTest {

    @Test
    @DisplayName("평점 1~5는 정상 생성된다")
    void write_validRating() {
        for (short rating = 1; rating <= 5; rating++) {
            ProductReview review = ProductReview.write(1L, 10L, 100L, rating, "후기");
            assertThat(review.getRating()).isEqualByComparingTo(BigDecimal.valueOf(rating));
        }
    }

    @Test
    @DisplayName("평점 0은 예외가 발생한다")
    void write_ratingZero() {
        assertThatThrownBy(() -> ProductReview.write(1L, 10L, 100L, (short) 0, "후기"))
            .isInstanceOf(BusinessRuleViolationException.class)
            .hasMessageContaining("평점은 1~5");
    }

    @Test
    @DisplayName("평점 6은 예외가 발생한다")
    void write_ratingSix() {
        assertThatThrownBy(() -> ProductReview.write(1L, 10L, 100L, (short) 6, "후기"))
            .isInstanceOf(BusinessRuleViolationException.class)
            .hasMessageContaining("평점은 1~5");
    }

    @Test
    @DisplayName("평점은 0.5점 단위만 허용한다")
    void write_halfPointOnly() {
        assertThat(ProductReview.write(1L, 10L, 100L, new BigDecimal("4.5"), "후기").getRating())
            .isEqualByComparingTo("4.5");
        assertThatThrownBy(() -> ProductReview.write(1L, 10L, 100L, new BigDecimal("4.2"), "후기"))
            .isInstanceOf(BusinessRuleViolationException.class);
    }
}
