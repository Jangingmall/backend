package com.jangingmall.backend.global.exception;

public class DomainException extends BusinessException {

    public DomainException(ErrorCode errorCode) {
        super(errorCode);
    }

    public ErrorCode getErrorCode() {
        return errorCode();
    }
}
