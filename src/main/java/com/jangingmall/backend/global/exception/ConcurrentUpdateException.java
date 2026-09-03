package com.jangingmall.backend.global.exception;

public class ConcurrentUpdateException extends BusinessException {

    public ConcurrentUpdateException() {
        super(ErrorCode.CONCURRENT_UPDATE);
    }

    public ConcurrentUpdateException(String message) {
        super(ErrorCode.CONCURRENT_UPDATE, message);
    }
}
