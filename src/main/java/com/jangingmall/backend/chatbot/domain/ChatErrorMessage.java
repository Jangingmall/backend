package com.jangingmall.backend.chatbot.domain;

public enum ChatErrorMessage {

    SESSION_NOT_FOUND("챗봇 세션을 찾을 수 없습니다"),
    SESSION_ALREADY_ENDED("이미 종료된 세션입니다"),
    SESSION_FORBIDDEN("해당 세션에 접근할 권한이 없습니다");

    private final String message;

    ChatErrorMessage(String message) {
        this.message = message;
    }

    public String message() {
        return message;
    }
}
