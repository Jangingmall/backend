package com.jangingmall.backend.content.application;

import com.jangingmall.backend.content.domain.AiContentClient;
import com.jangingmall.backend.content.domain.ContentGeneration;
import com.jangingmall.backend.content.domain.ContentGenerationRepository;
import com.jangingmall.backend.content.domain.GenerationStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI 초안(DRAFT_READY)에 대한 최종 렌더링을 GenAI에 요청한다. GenAI의 렌더 API는 동기 호출이라 완료까지 스레드를
 * 점유하므로 스케줄러 스레드가 아닌 aiGenerationExecutor에서 실행한다. 요청 경로는 세 가지다.
 * <ul>
 *   <li>스케줄러가 DRAFT_READY로 전이한 직후(자동)</li>
 *   <li>스케줄러가 DRAFT_READY 건을 마감 전까지 주기적으로 재요청(자동 재시도)</li>
 *   <li>사용자의 수동 요청·콘텐츠 승인 API({@link AiRenderApprovalRequestedEvent})</li>
 * </ul>
 * 같은 멱등성 키로 요청하므로 중복 호출되어도 GenAI에서 렌더가 중복 생성되지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiRenderApprovalAsyncExecutor {

    private static final int ERROR_DETAIL_LIMIT = 300;

    private final AiContentClient aiContentClient;
    private final ContentGenerationRepository generationRepository;

    /** 이 인스턴스에서 렌더 요청이 진행 중인 generationId. 같은 건의 중복 호출을 막는다. */
    private final Set<Long> inFlight = ConcurrentHashMap.newKeySet();

    @Async("aiGenerationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onRenderApprovalRequested(AiRenderApprovalRequestedEvent event) {
        render(event.generationId());
    }

    @Async("aiGenerationExecutor")
    public void requestRender(Long generationId) {
        render(generationId);
    }

    private void render(Long generationId) {
        if (!inFlight.add(generationId)) {
            log.debug("AI 렌더 요청 진행 중 — 중복 요청 생략 generationId={}", generationId);
            return;
        }
        try {
            ContentGeneration generation = generationRepository.findById(generationId).orElse(null);
            if (generation == null || generation.getStatus() != GenerationStatus.DRAFT_READY
                || generation.getJobId() == null) {
                log.info("AI 렌더 요청 생략 — DRAFT_READY 아님 generationId={} status={}",
                    generationId, generation == null ? null : generation.getStatus());
                return;
            }
            aiContentClient.approveRender(generation.getJobId(), generationId, generation.getProductId());
        } catch (Exception e) {
            // DRAFT_READY를 유지하므로 스케줄러가 마감 전까지 같은 키로 다시 요청한다.
            log.error("AI 렌더 요청 실패 — 재시도 대기 generationId={} reason={}", generationId, describe(e));
        } finally {
            inFlight.remove(generationId);
        }
    }

    private static String describe(Exception e) {
        String message = e.getClass().getSimpleName() + ": " + e.getMessage();
        return message.length() <= ERROR_DETAIL_LIMIT ? message : message.substring(0, ERROR_DETAIL_LIMIT) + "…";
    }
}
