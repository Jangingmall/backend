package com.jangingmall.backend.product.domain;

public enum ProductQnaErrorMessage {

    QUESTION_NOT_FOUND("문의를 찾을 수 없습니다"),
    ALREADY_ANSWERED("이미 답변이 등록된 문의입니다"),
    ANSWER_FORBIDDEN("해당 상품의 장인만 답변할 수 있습니다");

    private final String message;

    ProductQnaErrorMessage(String message) {
        this.message = message;
    }

    public String message() {
        return message;
    }
}
