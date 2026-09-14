package com.jangingmall.backend.content.domain;

public enum GenerationErrorMessage {

    NOT_FOUND("콘텐츠 생성 요청을 찾을 수 없습니다"),
    FORBIDDEN("해당 상품에 대한 권한이 없습니다");

    private final String message;

    GenerationErrorMessage(String message) {
        this.message = message;
    }

    public String message() {
        return message;
    }
}
