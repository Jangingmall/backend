package com.jangingmall.backend.global.exception;

public class DomainException extends BusinessException {

    public DomainException(ErrorCode errorCode) {
        super(errorCode);
    }

    public DomainException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public ErrorCode getErrorCode() {
        return errorCode();
    }
}
