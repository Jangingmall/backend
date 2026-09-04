package com.jangingmall.backend.global.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    // 400
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "입력값 유효성 오류"),
    REQUEST_INVALID(HttpStatus.BAD_REQUEST, "Request Body 누락"),
    REQUEST_BODY_MALFORMED(HttpStatus.BAD_REQUEST, "JSON 형식 오류"),

    // 401
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다"),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "토큰이 만료되었습니다"),
    TOKEN_MISMATCH(HttpStatus.UNAUTHORIZED, "토큰이 유효하지 않습니다"),

    // 403
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다"),

    // 404
    NOT_FOUND(HttpStatus.NOT_FOUND, "리소스를 찾을 수 없습니다"),

    // 409
    CONFLICT(HttpStatus.CONFLICT, "리소스 충돌이 발생했습니다"),
    CONCURRENT_UPDATE(HttpStatus.CONFLICT, "동시 수정 충돌이 발생했습니다"),

    // 410
    RESOURCE_EXPIRED(HttpStatus.GONE, "리소스가 만료되었습니다"),

    // 422
    BUSINESS_RULE_VIOLATION(HttpStatus.UNPROCESSABLE_ENTITY, "비즈니스 규칙 위반입니다"),

    // 429
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "요청 한도를 초과했습니다"),

    // 500
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다");

    private final HttpStatus httpStatus;
    private final String defaultMessage;

    ErrorCode(HttpStatus httpStatus, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }

    public String code() {
        return name();
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
