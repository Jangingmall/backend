package com.jangingmall.backend.product.domain;

import com.jangingmall.backend.global.exception.BusinessRuleViolationException;

import java.util.Set;

public enum ProductStatus {
    DRAFT,
    ON_SALE,
    SOLD_OUT,
    HIDDEN;

    private static final Set<ProductStatus> ARTISAN_MUTABLE = Set.of(DRAFT, ON_SALE, SOLD_OUT, HIDDEN);

    public void validateTransitionTo(ProductStatus next) {
        boolean allowed = switch (this) {
            case DRAFT -> next == ON_SALE || next == HIDDEN;
            case ON_SALE -> next == SOLD_OUT || next == HIDDEN;
            case SOLD_OUT -> next == ON_SALE || next == HIDDEN;
            case HIDDEN -> next == ON_SALE || next == DRAFT;
        };
        if (!allowed) {
            throw new BusinessRuleViolationException(ProductErrorMessage.INVALID_STATUS_TRANSITION.message());
        }
    }
}
