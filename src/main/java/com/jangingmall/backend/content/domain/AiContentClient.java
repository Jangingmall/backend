package com.jangingmall.backend.content.domain;

import java.util.List;

public interface AiContentClient {

    AiJobAccepted submitJob(Long generationId, Long productId, List<String> images, String productName, String howMade, String careTips);

    String getJobStatus(String jobId);

    /** AI 초안(draft)을 그대로 승인해 최종 렌더링을 요청한다. 동기 호출이며 완료 결과는 AI가 콜백으로 전달한다. */
    void approveRender(String jobId, Long generationId, Long productId);

    void syncProduct(AiProductSyncPayload payload);

    void updateProduct(Long productId, AiProductUpdatePayload payload);

    /** 상태 한 필드만 갱신한다(챗봇이 재임베딩하지 않는 경로). */
    void updateProductStatus(Long productId, String status);

    void deleteProduct(Long productId);
}
