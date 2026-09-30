package com.jangingmall.backend.content.domain;

import java.util.List;

public interface AiContentClient {

    AiJobAccepted submitJob(Long generationId, Long productId, List<String> images, String productName, String howMade, String careTips);

    String getJobStatus(String jobId);

    void approveRender(String jobId, Long generationId);

    void syncProduct(AiProductSyncPayload payload);

    void updateProduct(Long productId, AiProductUpdatePayload payload);

    /** 상태 한 필드만 갱신한다(챗봇이 재임베딩하지 않는 경로). */
    void updateProductStatus(Long productId, String status);

    void deleteProduct(Long productId);
}
