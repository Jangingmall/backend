package com.jangingmall.backend.content.domain;

import java.util.List;

public interface AiContentClient {
    /**
     * AI 서버에 콘텐츠 생성을 요청하고 생성된 블록 JSON 문자열을 반환한다.
     * 블록 구조: [{order, tag, text, imageUrl}]
     */
    String requestGeneration(Long generationId, Long productId, List<String> images, String productName, String howMade, String careTips);
}
