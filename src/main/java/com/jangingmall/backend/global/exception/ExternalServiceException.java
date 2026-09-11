package com.jangingmall.backend.global.exception;

public class ExternalServiceException extends BusinessException {

    public ExternalServiceException(String message) {
        super(ErrorCode.EXTERNAL_SERVICE_ERROR, message);
    }
}
