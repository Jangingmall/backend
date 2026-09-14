package com.jangingmall.backend.product.domain;

public enum ProductErrorMessage {

    NOT_FOUND("상품을 찾을 수 없습니다"),
    FORBIDDEN("해당 상품에 대한 권한이 없습니다"),
    INVALID_STATUS_TRANSITION("허용되지 않은 상태 전이입니다"),
    INVALID_PRICE("가격은 0보다 커야 합니다"),
    INVALID_STOCK("재고는 0 이상이어야 합니다");

    private final String message;

    ProductErrorMessage(String message) {
        this.message = message;
    }

    public String message() {
        return message;
    }
}
