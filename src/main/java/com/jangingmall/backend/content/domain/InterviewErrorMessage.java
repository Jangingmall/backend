package com.jangingmall.backend.content.domain;

public enum InterviewErrorMessage {

    NOT_FOUND("취재 데이터를 찾을 수 없습니다"),
    ALREADY_EXISTS("이미 취재 데이터가 등록된 상품입니다"),
    FORBIDDEN("해당 상품에 대한 권한이 없습니다");

    private final String message;

    InterviewErrorMessage(String message) {
        this.message = message;
    }

    public String message() {
        return message;
    }
}
