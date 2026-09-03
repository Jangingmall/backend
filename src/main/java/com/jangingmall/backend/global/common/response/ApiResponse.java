package com.jangingmall.backend.global.common.response;

public record ApiResponse<T>(boolean success, int status, T data) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, 200, data);
    }

    public static <T> ApiResponse<T> created(T data) {
        return new ApiResponse<>(true, 201, data);
    }

    public static ApiResponse<Void> noContent() {
        return new ApiResponse<>(true, 204, null);
    }

    public static <T> ApiResponse<T> of(int status, T data) {
        return new ApiResponse<>(true, status, data);
    }
}
