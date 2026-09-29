package com.jangingmall.backend.content.application;

import com.jangingmall.backend.content.domain.AiContentClient;
import com.jangingmall.backend.content.domain.AiJobAccepted;
import com.jangingmall.backend.content.domain.ContentGeneration;
import com.jangingmall.backend.content.domain.ContentGenerationRepository;
import com.jangingmall.backend.content.domain.GenerationErrorMessage;
import com.jangingmall.backend.content.infrastructure.DiscordNotificationService;
import com.jangingmall.backend.global.exception.NotFoundException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionPhase;

import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Component
public class GenerationAsyncExecutor {

    private static final int MAX_RETRY_COUNT = 2;
    private static final long BASE_DELAY_MS = 1_000L;
    private static final long MAX_DELAY_MS = 8_000L;

    private final ContentGenerationRepository generationRepository;
    private final AiContentClient aiContentClient;
    private final DiscordNotificationService discordNotificationService;
    private final Counter retryCounter;
    private final Counter failedCounter;

    public GenerationAsyncExecutor(ContentGenerationRepository generationRepository,
                                   AiContentClient aiContentClient,
                                   DiscordNotificationService discordNotificationService,
                                   MeterRegistry meterRegistry) {
        this.generationRepository = generationRepository;
        this.aiContentClient = aiContentClient;
        this.discordNotificationService = discordNotificationService;
        this.retryCounter = Counter.builder("ai_generation_retry")
            .description("AI job 제출 재시도 횟수")
            .register(meterRegistry);
        this.failedCounter = Counter.builder("ai_generation_failed")
            .description("AI job 제출 최종 실패 횟수")
            .register(meterRegistry);
    }

    @Async("aiGenerationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onGenerationRequested(GenerationRequestedEvent event) {
        Long generationId = event.generationId();
        GenerationCommand.Request command = event.command();

        ContentGeneration generation = generationRepository.findByIdAndProductId(generationId, command.productId())
            .orElseThrow(() -> new NotFoundException(GenerationErrorMessage.NOT_FOUND.message()));

        discordNotificationService.notifyGenerationRequested(
            generationId, command.productId(), command.productName(), command.images().size()
        );

        Exception lastException = null;
        for (int attempt = 0; attempt <= MAX_RETRY_COUNT; attempt++) {
            if (attempt > 0) {
                retryCounter.increment();
                sleepWithBackoff(attempt);
            }
            try {
                AiJobAccepted accepted = aiContentClient.submitJob(
                    generationId,
                    command.productId(),
                    command.images(),
                    command.productName(),
                    command.howMade(),
                    command.careTips()
                );
                generation.markQueued(
                    accepted.jobId(),
                    accepted.requestId(),
                    generationId.toString(),
                    accepted.statusUrl()
                );
                generationRepository.save(generation);
                log.info("AI job 제출 완료 generationId={} jobId={}", generationId, accepted.jobId());
                discordNotificationService.notifyGenerationSucceeded(generationId, command.productId(), accepted.jobId());
                return;
            } catch (Exception e) {
                lastException = e;
                log.warn("AI job 제출 실패 generationId={} attempt={} reason={}", generationId, attempt + 1, e.getMessage());
            }
        }

        failedCounter.increment();
        generation.fail();
        generationRepository.save(generation);
        String failReason = lastException != null ? lastException.getMessage() : "unknown";
        log.error("AI job 제출 최종 실패 generationId={} reason={}", generationId, failReason);
        discordNotificationService.notifyGenerationFailed(generationId, command.productId(), failReason);
    }

    private void sleepWithBackoff(int attempt) {
        long delay = Math.min(BASE_DELAY_MS * (1L << attempt), MAX_DELAY_MS);
        long jitter = ThreadLocalRandom.current().nextLong(0, delay / 2 + 1);
        try {
            Thread.sleep(delay + jitter);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
