package com.jangingmall.backend.product.domain;

import com.jangingmall.backend.global.exception.BusinessRuleViolationException;
import com.jangingmall.backend.global.exception.ForbiddenException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductTest {

    @Test
    @DisplayName("상품 생성 시 기본 상태는 DRAFT다")
    void createProduct() {
        Product product = Product.create(1L, null, null, "청자 다완", "설명", 85000, 10, null);

        assertThat(product.getStatus()).isEqualTo(ProductStatus.DRAFT);
        assertThat(product.getArtisanId()).isEqualTo(1L);
        assertThat(product.getTitle()).isEqualTo("청자 다완");
    }

    @Test
    @DisplayName("가격이 0 이하이면 상품을 생성할 수 없다")
    void createWithInvalidPrice() {
        assertThatThrownBy(() -> Product.create(1L, null, null, "제목", "설명", 0, 10, null))
            .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    @DisplayName("재고가 음수이면 상품을 생성할 수 없다")
    void createWithNegativeStock() {
        assertThatThrownBy(() -> Product.create(1L, null, null, "제목", "설명", 1000, -1, null))
            .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    @DisplayName("소유자가 아닌 장인이 수정하면 ForbiddenException이 발생한다")
    void updateByNonOwner() {
        Product product = Product.create(1L, null, null, "제목", "설명", 1000, 5, null);

        assertThatThrownBy(() -> product.update(null, null, "수정제목", "수정설명", 2000, 3, null, 999L))
            .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("소유자가 상태를 변경할 수 있다")
    void changeStatusByOwner() {
        Product product = Product.create(1L, null, null, "제목", "설명", 1000, 5, null);

        product.changeStatus(ProductStatus.ON_SALE, 1L);

        assertThat(product.getStatus()).isEqualTo(ProductStatus.ON_SALE);
    }

    @Test
    @DisplayName("소유자가 아닌 장인이 상태를 변경하면 ForbiddenException이 발생한다")
    void changeStatusByNonOwner() {
        Product product = Product.create(1L, null, null, "제목", "설명", 1000, 5, null);

        assertThatThrownBy(() -> product.changeStatus(ProductStatus.ON_SALE, 999L))
            .isInstanceOf(ForbiddenException.class);
    }
}
