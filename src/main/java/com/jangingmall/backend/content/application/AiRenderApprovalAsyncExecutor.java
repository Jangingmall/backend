package com.jangingmall.backend.content.application;

import com.jangingmall.backend.content.domain.AiContentClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionPhase;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiRenderApprovalAsyncExecutor {

    private final AiContentClient aiContentClient;

    @Async("aiGenerationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRenderApprovalRequested(AiRenderApprovalRequestedEvent event) {
        try {
            aiContentClient.approveRender(event.jobId(), event.generationId());
            log.info("AI 렌더 승인 완료 jobId={} generationId={}", event.jobId(), event.generationId());
        } catch (Exception e) {
            log.error("AI 렌더 승인 요청 실패 generationId={} jobId={} reason={}",
                event.generationId(), event.jobId(), e.getMessage());
        }
    }
}
