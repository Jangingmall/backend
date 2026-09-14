package com.jangingmall.backend.product.domain;

import com.jangingmall.backend.global.exception.BusinessRuleViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductStatusTest {

    @Test
    @DisplayName("DRAFT에서 ON_SALE로 전이할 수 있다")
    void draftToOnSale() {
        assertThatCode(() -> ProductStatus.DRAFT.validateTransitionTo(ProductStatus.ON_SALE))
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("DRAFT에서 HIDDEN으로 전이할 수 있다")
    void draftToHidden() {
        assertThatCode(() -> ProductStatus.DRAFT.validateTransitionTo(ProductStatus.HIDDEN))
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("ON_SALE에서 SOLD_OUT으로 전이할 수 있다")
    void onSaleToSoldOut() {
        assertThatCode(() -> ProductStatus.ON_SALE.validateTransitionTo(ProductStatus.SOLD_OUT))
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("ON_SALE에서 HIDDEN으로 전이할 수 있다")
    void onSaleToHidden() {
        assertThatCode(() -> ProductStatus.ON_SALE.validateTransitionTo(ProductStatus.HIDDEN))
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("SOLD_OUT에서 ON_SALE로 전이할 수 있다")
    void soldOutToOnSale() {
        assertThatCode(() -> ProductStatus.SOLD_OUT.validateTransitionTo(ProductStatus.ON_SALE))
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("HIDDEN에서 DRAFT로 전이할 수 있다")
    void hiddenToDraft() {
        assertThatCode(() -> ProductStatus.HIDDEN.validateTransitionTo(ProductStatus.DRAFT))
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("DRAFT에서 SOLD_OUT으로 직접 전이할 수 없다")
    void draftToSoldOutNotAllowed() {
        assertThatThrownBy(() -> ProductStatus.DRAFT.validateTransitionTo(ProductStatus.SOLD_OUT))
            .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    @DisplayName("ON_SALE에서 DRAFT로 되돌아갈 수 없다")
    void onSaleToDraftNotAllowed() {
        assertThatThrownBy(() -> ProductStatus.ON_SALE.validateTransitionTo(ProductStatus.DRAFT))
            .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    @DisplayName("SOLD_OUT에서 DRAFT로 전이할 수 없다")
    void soldOutToDraftNotAllowed() {
        assertThatThrownBy(() -> ProductStatus.SOLD_OUT.validateTransitionTo(ProductStatus.DRAFT))
            .isInstanceOf(BusinessRuleViolationException.class);
    }
}
