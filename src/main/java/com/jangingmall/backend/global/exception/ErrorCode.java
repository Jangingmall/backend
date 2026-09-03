package com.jangingmall.backend.global.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    // 400
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "입력값 유효성 오류"),
    REQUEST_INVALID(HttpStatus.BAD_REQUEST, "REQUEST_INVALID", "Request Body 누락"),
    REQUEST_BODY_MALFORMED(HttpStatus.BAD_REQUEST, "REQUEST_BODY_MALFORMED", "JSON 형식 오류"),

    // 401
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "인증이 필요합니다"),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "TOKEN_EXPIRED", "토큰이 만료되었습니다"),
    TOKEN_MISMATCH(HttpStatus.UNAUTHORIZED, "TOKEN_MISMATCH", "토큰이 유효하지 않습니다"),

    // 403
    FORBIDDEN(HttpStatus.FORBIDDEN, "FORBIDDEN", "접근 권한이 없습니다"),

    // 404
    NOT_FOUND(HttpStatus.NOT_FOUND, "NOT_FOUND", "리소스를 찾을 수 없습니다"),

    // 409
    CONFLICT(HttpStatus.CONFLICT, "CONFLICT", "리소스 충돌이 발생했습니다"),
    CONCURRENT_UPDATE(HttpStatus.CONFLICT, "CONCURRENT_UPDATE", "동시 수정 충돌이 발생했습니다"),

    // 410
    RESOURCE_EXPIRED(HttpStatus.GONE, "RESOURCE_EXPIRED", "리소스가 만료되었습니다"),

    // 422
    BUSINESS_RULE_VIOLATION(HttpStatus.UNPROCESSABLE_ENTITY, "BUSINESS_RULE_VIOLATION", "비즈니스 규칙 위반입니다"),

    // 429
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_REQUESTS", "요청 한도를 초과했습니다"),

    // 500
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "서버 내부 오류가 발생했습니다");

    private final HttpStatus httpStatus;
    private final String code;
    private final String defaultMessage;

    ErrorCode(HttpStatus httpStatus, String code, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }

    public String code() {
        return code;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
