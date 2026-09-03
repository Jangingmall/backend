package com.jangingmall.backend.global.common.response;

import com.jangingmall.backend.global.exception.ErrorCode;

public record ApiErrorResponse(boolean success, int status, String errorCode, String message) {

    public static ApiErrorResponse of(ErrorCode code) {
        return new ApiErrorResponse(false, code.httpStatus().value(), code.code(), code.defaultMessage());
    }

    public static ApiErrorResponse of(ErrorCode code, String message) {
        return new ApiErrorResponse(false, code.httpStatus().value(), code.code(), message);
    }
}
