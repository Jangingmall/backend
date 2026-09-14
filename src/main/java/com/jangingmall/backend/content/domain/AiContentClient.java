package com.jangingmall.backend.content.domain;

import java.util.List;

public interface AiContentClient {
    void requestGeneration(Long generationId, Long productId, List<String> images, String productName, String howMade, String careTips);
}
