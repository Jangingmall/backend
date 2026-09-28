package com.jangingmall.backend.content.application;

import com.jangingmall.backend.content.domain.AiContentClient;
import com.jangingmall.backend.content.domain.ContentGeneration;
import com.jangingmall.backend.content.domain.ContentGenerationRepository;
import com.jangingmall.backend.content.domain.GenerationStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class GenerationDeadlineScheduler {

    private static final String AI_STATUS_DRAFT_READY = "DRAFT_READY";
    private static final String AI_STATUS_FAILED = "FAILED";

    private final ContentGenerationRepository generationRepository;
    private final GenerationProperties properties;
    private final AiContentClient aiContentClient;

    @Scheduled(fixedDelayString = "${ai.generation.scan-millis:60000}")
    @Transactional
    public void expireOverdueGenerations() {
        LocalDateTime deadline = LocalDateTime.now().minusSeconds(properties.deadlineSeconds());
        List<ContentGeneration> overdue = generationRepository.findAllByStatusAndRequestedAtBefore(
            GenerationStatus.QUEUED, deadline
        );

        if (overdue.isEmpty()) {
            return;
        }

        for (ContentGeneration generation : overdue) {
            generation.fail();
            generationRepository.save(generation);
            log.warn("AI 생성 데드라인 초과 — FAILED 처리 generationId={} productId={} requestedAt={}",
                generation.getId(), generation.getProductId(), generation.getRequestedAt());
        }

        log.info("AI 생성 데드라인 스캔 완료 — 만료 처리 건수={}", overdue.size());
    }

    @Scheduled(fixedDelayString = "${ai.generation.scan-millis:60000}")
    public void pollQueuedGenerations() {
        List<Long> queuedIds = loadQueuedIds();

        for (Long generationId : queuedIds) {
            String jobId = loadJobId(generationId);
            if (jobId == null) {
                continue;
            }

            String aiStatus = fetchAiStatus(generationId, jobId);
            if (aiStatus == null) {
                continue;
            }

            if (AI_STATUS_DRAFT_READY.equals(aiStatus)) {
                applyDraftReady(generationId, jobId);
            } else if (AI_STATUS_FAILED.equals(aiStatus)) {
                applyFailed(generationId, jobId);
            }
        }
    }

    @Transactional(readOnly = true)
    public List<Long> loadQueuedIds() {
        return generationRepository.findAllByStatus(GenerationStatus.QUEUED)
            .stream()
            .map(ContentGeneration::getId)
            .toList();
    }

    @Transactional(readOnly = true)
    public String loadJobId(Long generationId) {
        return generationRepository.findById(generationId)
            .map(ContentGeneration::getJobId)
            .orElse(null);
    }

    @Transactional
    public void applyDraftReady(Long generationId, String jobId) {
        generationRepository.findById(generationId).ifPresent(gen -> {
            gen.markDraftReady();
            generationRepository.save(gen);
            log.info("AI DRAFT_READY 전이 generationId={} jobId={}", generationId, jobId);
        });
    }

    @Transactional
    public void applyFailed(Long generationId, String jobId) {
        generationRepository.findById(generationId).ifPresent(gen -> {
            gen.fail();
            generationRepository.save(gen);
            log.warn("AI 작업 실패 확인 generationId={} jobId={}", generationId, jobId);
        });
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
