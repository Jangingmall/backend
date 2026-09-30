package com.jangingmall.backend.content.application;

import com.jangingmall.backend.content.domain.AiContentClient;
import com.jangingmall.backend.content.domain.AiJobAccepted;
import com.jangingmall.backend.content.domain.ContentGeneration;
import com.jangingmall.backend.content.domain.ContentGenerationRepository;
import com.jangingmall.backend.content.domain.GenerationStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * scan-millis(기본 60초)마다 두 가지를 처리한다.
 * <ol>
 *   <li>QUEUED: AI 작업 상태를 조회한다. 요청 후 deadline-seconds(기본 1861초 ≈ 31분) 동안 폴링하고,
 *       데드라인이 지난 건은 마지막으로 한 번 더 조회한 뒤에도 DRAFT_READY가 아니면 FAILED 처리한다.</li>
 *   <li>PROCESSING(AI 제출이 아직 접수되지 않은 건): 오류 종류(4xx 포함)와 무관하게 데드라인까지 주기적으로
 *       재제출한다. 접수되면 QUEUED, 데드라인이 지나면 FAILED. 재제출은 같은 멱등성 키(generationId)를 쓰므로
 *       AI 쪽에서 중복 작업이 생기지 않는다.</li>
 * </ol>
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
        pollQueued();
        retryUnsubmitted();
    }

    private void pollQueued() {
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
                if (transition(generationId, GenerationStatus.QUEUED, ContentGeneration::markDraftReady)) {
                    draftReady++;
                    log.info("AI DRAFT_READY 전이 generationId={} jobId={}", generationId, jobId);
                }
            } else if (AI_STATUS_FAILED.equals(aiStatus)) {
                if (transition(generationId, GenerationStatus.QUEUED, ContentGeneration::fail)) {
                    failed++;
                    log.warn("AI 작업 실패 확인 generationId={} jobId={}", generationId, jobId);
                }
            } else if (overdue) {
                if (transition(generationId, GenerationStatus.QUEUED, ContentGeneration::fail)) {
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

    /**
     * AI에 아직 접수되지 않은 PROCESSING 건을 재제출한다. 실패는 오류 종류와 무관하게 대기 상태를 유지하고,
     * 데드라인이 지나면 FAILED 처리한다. 요청 직후에는 GenerationAsyncExecutor가 제출 중일 수 있어
     * scan 주기 이내의 건은 건드리지 않는다.
     */
    private void retryUnsubmitted() {
        List<ContentGeneration> processing = generationRepository.findAllByStatus(GenerationStatus.PROCESSING);
        if (processing.isEmpty()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime deadline = now.minusSeconds(properties.deadlineSeconds());
        LocalDateTime graceCutoff = now.minus(Duration.ofMillis(properties.scanMillis()));
        int resubmitted = 0;
        int pending = 0;
        int expired = 0;

        for (ContentGeneration generation : processing) {
            Long generationId = generation.getId();
            if (generation.getRequestedAt().isBefore(deadline)) {
                if (transition(generationId, GenerationStatus.PROCESSING, ContentGeneration::fail)) {
                    expired++;
                    log.warn("AI 제출 데드라인 초과 — FAILED 처리 generationId={} requestedAt={}",
                        generationId, generation.getRequestedAt());
                }
                continue;
            }
            if (generation.getRequestedAt().isAfter(graceCutoff)) {
                continue;
            }
            if (resubmit(generation)) {
                resubmitted++;
            } else {
                pending++;
            }
        }

        log.info("AI 제출 재시도 완료 — 대상={} 접수={} 대기유지={} 데드라인만료={}",
            processing.size(), resubmitted, pending, expired);
    }

    private boolean resubmit(ContentGeneration generation) {
        Long generationId = generation.getId();
        try {
            AiJobAccepted accepted = aiContentClient.submitJob(
                generationId,
                generation.getProductId(),
                splitImages(generation.getImages()),
                generation.getProductName(),
                generation.getHowMade(),
                generation.getCareTips()
            );
            boolean queued = transition(generationId, GenerationStatus.PROCESSING, gen ->
                gen.markQueued(accepted.jobId(), accepted.requestId(), generationId.toString(), accepted.statusUrl()));
            if (queued) {
                log.info("AI job 재제출 접수 generationId={} jobId={}", generationId, accepted.jobId());
            }
            return queued;
        } catch (Exception e) {
            log.warn("AI job 재제출 실패 — 대기 유지 generationId={} reason={}", generationId, e.getMessage());
            return false;
        }
    }

    private static List<String> splitImages(String images) {
        if (images == null || images.isBlank()) {
            return List.of();
        }
        return Arrays.stream(images.split(","))
            .map(String::trim)
            .filter(image -> !image.isEmpty())
            .toList();
    }

    private boolean transition(Long generationId, GenerationStatus expected, Consumer<ContentGeneration> change) {
        // 조회 이후 콜백 등으로 상태가 바뀌었을 수 있으므로 최신 상태를 다시 읽고 기대한 상태일 때만 전이한다.
        return generationRepository.findById(generationId)
            .filter(gen -> gen.getStatus() == expected)
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
