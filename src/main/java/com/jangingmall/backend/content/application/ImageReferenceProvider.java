package com.jangingmall.backend.content.application;

import java.util.Optional;

/**
 * AI에 넘길 사진 참조(요청의 images 항목)를 AI가 내려받을 수 있는 URL로 바꾸는 방법 하나.
 * 새 형태의 참조가 생기면 구현체를 하나 추가하면 된다. 리졸버는 {@code @Order} 순서대로 처음 지원하는 구현을 쓴다.
 */
public interface ImageReferenceProvider {

    /** 이 구현이 해당 참조 형태를 처리할 수 있는지. */
    boolean supports(String reference);

    /** AI가 내려받을 URL. 찾을 수 없으면 빈 값. */
    Optional<String> toUrl(String reference);
}
