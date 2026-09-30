package com.jangingmall.backend.content.domain;

public enum GenerationErrorMessage {

    NOT_FOUND("콘텐츠 생성 요청을 찾을 수 없습니다"),
    FORBIDDEN("해당 상품에 대한 권한이 없습니다"),
    RENDER_NOT_ALLOWED("AI 초안이 준비된 생성 요청만 렌더링을 요청할 수 있습니다"),
    ALREADY_FAILED("이미 실패 처리된 생성 요청입니다"),
    LATE_RESULT_SUPERSEDED("새로운 생성 요청이 있거나 콘텐츠가 이미 수정되어 늦게 도착한 결과를 반영할 수 없습니다");

    private final String message;

    GenerationErrorMessage(String message) {
        this.message = message;
    }

    public String message() {
        return message;
    }
}
