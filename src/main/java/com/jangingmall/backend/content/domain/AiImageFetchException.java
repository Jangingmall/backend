package com.jangingmall.backend.content.domain;

/**
 * AI 서버로 보낼 상품 이미지를 내려받지 못했거나 이미지가 아닌 응답을 받았을 때 던진다.
 * 입력 자체가 잘못된 경우라 재시도해도 결과가 같으므로 재시도 대상이 아니다.
 */
public class AiImageFetchException extends RuntimeException {

    public AiImageFetchException(String message, Throwable cause) {
        super(message, cause);
    }

    public AiImageFetchException(String message) {
        super(message);
    }
}
