package com.jangingmall.backend.content.domain;

import java.util.List;

public interface AiContentClient {

    AiJobAccepted submitJob(Long generationId, Long productId, List<String> images, String productName, String howMade, String careTips);

    void approveRender(String jobId, Long generationId);

    void syncProduct(AiProductSyncPayload payload);

    void updateProduct(Long productId, AiProductUpdatePayload payload);

    void deleteProduct(Long productId);
}
