package com.jangingmall.backend.content.application;

import com.jangingmall.backend.content.domain.AiContentClient;
import com.jangingmall.backend.content.domain.ContentGeneration;
import com.jangingmall.backend.content.domain.ContentGenerationRepository;
import com.jangingmall.backend.content.domain.GenerationStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * QUEUED generation의 AI 작업 상태를 scan-millis(기본 60초)마다 조회한다.
 * 요청 후 deadline-seconds(기본 1861초 ≈ 31분) 동안 폴링하고,
 * 데드라인이 지난 건은 마지막으로 한 번 더 조회한 뒤에도 DRAFT_READY가 아니면 FAILED 처리한다.
 * COMPLETED 전이는 AI 콜백(AiCallbackController)이 담당한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GenerationDeadlineScheduler {

    static final String AI_STATUS_DRAFT_READY = "DRAFT_READY";
    static final String AI_STATUS_COMPLETED = "COMPLETED";
    static final String AI_STATUS_FAILED = "FAILED";
    static final Set<String> AI_IN_PROGRESS_STATUSES = Set.of(
        "QUEUED", "ANALYZING", "EXTRACTING", "GENERATING_BACKGROUNDS",
        "COMPOSING", "VERIFYING", "RENDERING", "DELIVERING"
    );

    private final ContentGenerationRepository generationRepository;
    private final GenerationProperties properties;
    private final AiContentClient aiContentClient;

    @Scheduled(fixedDelayString = "${ai.generation.scan-millis:60000}")
    public void pollQueuedGenerations() {
        List<ContentGeneration> queued = generationRepository.findAllByStatus(GenerationStatus.QUEUED);
        if (queued.isEmpty()) {
            return;
        }

        LocalDateTime deadline = LocalDateTime.now().minusSeconds(properties.deadlineSeconds());
        int draftReady = 0;
        int failed = 0;
        int expired = 0;

        for (ContentGeneration generation : queued) {
            Long generationId = generation.getId();
            String jobId = generation.getJobId();
            boolean overdue = generation.getRequestedAt().isBefore(deadline);
            String aiStatus = jobId == null ? null : fetchAiStatus(generationId, jobId);

            if (AI_STATUS_DRAFT_READY.equals(aiStatus)) {
                if (transition(generationId, ContentGeneration::markDraftReady)) {
                    draftReady++;
                    log.info("AI DRAFT_READY 전이 generationId={} jobId={}", generationId, jobId);
                }
            } else if (AI_STATUS_FAILED.equals(aiStatus)) {
                if (transition(generationId, ContentGeneration::fail)) {
                    failed++;
                    log.warn("AI 작업 실패 확인 generationId={} jobId={}", generationId, jobId);
                }
            } else if (overdue) {
                if (transition(generationId, ContentGeneration::fail)) {
                    expired++;
                    log.warn("AI 생성 데드라인 초과 — FAILED 처리 generationId={} jobId={} requestedAt={} lastAiStatus={}",
                        generationId, jobId, generation.getRequestedAt(), aiStatus);
                }
            } else {
                logPending(generationId, jobId, aiStatus);
            }
        }

        log.info("AI 생성 상태 폴링 완료 — 대상={} DRAFT_READY={} FAILED={} 데드라인만료={}",
            queued.size(), draftReady, failed, expired);
    }

    private boolean transition(Long generationId, Consumer<ContentGeneration> change) {
        // 조회 이후 콜백 등으로 상태가 바뀌었을 수 있으므로 최신 상태를 다시 읽고 QUEUED일 때만 전이한다.
        return generationRepository.findById(generationId)
            .filter(gen -> gen.getStatus() == GenerationStatus.QUEUED)
            .map(gen -> {
                change.accept(gen);
                generationRepository.save(gen);
                return true;
            })
            .orElse(false);
    }

    private void logPending(Long generationId, String jobId, String aiStatus) {
        if (aiStatus == null || AI_IN_PROGRESS_STATUSES.contains(aiStatus)) {
            log.debug("AI 작업 진행 중 generationId={} jobId={} aiStatus={}", generationId, jobId, aiStatus);
        } else if (AI_STATUS_COMPLETED.equals(aiStatus)) {
            log.info("AI COMPLETED, BE 콜백 대기 generationId={} jobId={}", generationId, jobId);
        } else {
            log.warn("알 수 없는 AI 상태 generationId={} jobId={} aiStatus={}", generationId, jobId, aiStatus);
        }
    }

    private String fetchAiStatus(Long generationId, String jobId) {
        try {
            return aiContentClient.getJobStatus(jobId);
        } catch (Exception e) {
            log.error("AI 상태 조회 실패 generationId={} jobId={} reason={}", generationId, jobId, e.getMessage());
            return null;
        }
    }
}
