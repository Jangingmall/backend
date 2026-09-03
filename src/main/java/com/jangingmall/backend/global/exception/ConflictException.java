package com.jangingmall.backend.global.exception;

public class ConflictException extends BusinessException {

    public ConflictException() {
        super(ErrorCode.CONFLICT);
    }

    public ConflictException(String message) {
        super(ErrorCode.CONFLICT, message);
    }
}
