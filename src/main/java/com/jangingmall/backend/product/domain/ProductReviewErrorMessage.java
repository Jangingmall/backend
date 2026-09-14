package com.jangingmall.backend.product.domain;

public enum ProductReviewErrorMessage {

    INVALID_RATING("평점은 1~5 사이여야 합니다"),
    ALREADY_REVIEWED("이미 후기를 작성한 주문 항목입니다"),
    REVIEW_NOT_FOUND("후기를 찾을 수 없습니다");

    private final String message;

    ProductReviewErrorMessage(String message) {
        this.message = message;
    }

    public String message() {
        return message;
    }
}
