package com.jangingmall.backend.content.domain;

public enum ContentErrorMessage {
    NOT_FOUND("콘텐츠를 찾을 수 없습니다"),
    FORBIDDEN("콘텐츠에 대한 접근 권한이 없습니다"),
    CONCURRENT_UPDATE("다른 사용자가 먼저 수정했습니다. 최신 내용을 다시 조회해 주세요"),
    INVALID_STATUS_TRANSITION("현재 상태에서 허용되지 않는 콘텐츠 상태 전이입니다"),
    CONTENT_NOT_APPROVED("콘텐츠가 승인(APPROVED) 상태여야 게시할 수 있습니다"),
    BLOCK_NOT_FOUND("해당 순서의 블록을 찾을 수 없습니다"),
    EDIT_NOT_ALLOWED("DRAFT 또는 REJECTED 상태에서만 편집할 수 있습니다");

    private final String text;

    ContentErrorMessage(String text) {
        this.text = text;
    }

    public String message() {
        return text;
    }
}
