package com.jangingmall.backend.content.infrastructure;

import com.jangingmall.backend.content.domain.AiContentClient;
import com.jangingmall.backend.content.domain.AiJobAccepted;
import com.jangingmall.backend.content.domain.AiProductSyncPayload;
import com.jangingmall.backend.content.domain.AiProductUpdatePayload;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@ConditionalOnMissingBean(AiContentClient.class)
class NoOpAiContentClient implements AiContentClient {

    @Override
    public AiJobAccepted submitJob(Long generationId, Long productId, List<String> images,
        String productName, String howMade, String careTips) {
        log.warn("AI_BASE_URL 미설정 — submitJob skip generationId={}", generationId);
        return new AiJobAccepted(null, null, null);
    }

    @Override
    public String getJobStatus(String jobId) {
        log.warn("AI_BASE_URL 미설정 — getJobStatus skip jobId={}", jobId);
        return "UNKNOWN";
    }

    @Override
    public void approveRender(String jobId, Long generationId, Long productId) {
        log.warn("AI_BASE_URL 미설정 — approveRender skip jobId={}", jobId);
    }

    @Override
    public void syncProduct(AiProductSyncPayload payload) {
        log.warn("AI_BASE_URL 미설정 — syncProduct skip productId={}", payload.product().product_id());
    }

    @Override
    public void updateProduct(Long productId, AiProductUpdatePayload payload) {
        log.warn("AI_BASE_URL 미설정 — updateProduct skip productId={}", productId);
    }

    @Override
    public void deleteProduct(Long productId) {
        log.warn("AI_BASE_URL 미설정 — deleteProduct skip productId={}", productId);
    }
}
