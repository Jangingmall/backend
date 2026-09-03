package com.jangingmall.backend.global.common.response;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

@RestControllerAdvice
public class GlobalResponseAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        // String, byte[] 컨버터는 래핑하지 않음 (ClassCastException 방지)
        if (StringHttpMessageConverter.class.isAssignableFrom(converterType)) {
            return false;
        }
        if (ByteArrayHttpMessageConverter.class.isAssignableFrom(converterType)) {
            return false;
        }
        return true;
    }

    @Override
    public Object beforeBodyWrite(
            Object body,
            MethodParameter returnType,
            MediaType selectedContentType,
            Class<? extends HttpMessageConverter<?>> selectedConverterType,
            ServerHttpRequest request,
            ServerHttpResponse response) {

        // SSE 스트림은 래핑하지 않음
        if (MediaType.TEXT_EVENT_STREAM.isCompatibleWith(selectedContentType)) {
            return body;
        }

        // 이미 래핑된 응답은 그대로 통과 (double-wrap, error response 방지)
        if (body instanceof ApiResponse<?> || body instanceof ApiErrorResponse) {
            return body;
        }

        // 204 No Content는 null 반환
        if (body == null) {
            return null;
        }

        int httpStatus = 200;
        if (response instanceof ServletServerHttpResponse servletResponse) {
            httpStatus = servletResponse.getServletResponse().getStatus();
        }

        return new ApiResponse<>(true, httpStatus, body);
    }
}
