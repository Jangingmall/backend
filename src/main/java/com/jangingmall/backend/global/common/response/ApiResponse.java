package com.jangingmall.backend.global.common.response;

public record ApiResponse<T>(boolean success, int status, T data) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, 200, data);
    }
}
