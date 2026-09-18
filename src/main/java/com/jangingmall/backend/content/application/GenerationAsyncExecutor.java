package com.jangingmall.backend.content.application;

import com.jangingmall.backend.content.domain.AiContentClient;
import com.jangingmall.backend.content.domain.AiJobAccepted;
import com.jangingmall.backend.content.domain.ContentGeneration;
import com.jangingmall.backend.content.domain.ContentGenerationRepository;
import com.jangingmall.backend.content.domain.GenerationErrorMessage;
import com.jangingmall.backend.global.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionPhase;

@Slf4j
@Component
@RequiredArgsConstructor
public class GenerationAsyncExecutor {

    private static final int MAX_RETRY_COUNT = 2;

    private final ContentGenerationRepository generationRepository;
    private final AiContentClient aiContentClient;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onGenerationRequested(GenerationRequestedEvent event) {
        Long generationId = event.generationId();
        GenerationCommand.Request command = event.command();

        ContentGeneration generation = generationRepository.findByIdAndProductId(generationId, command.productId())
            .orElseThrow(() -> new NotFoundException(GenerationErrorMessage.NOT_FOUND.message()));

        Exception lastException = null;
        for (int attempt = 0; attempt <= MAX_RETRY_COUNT; attempt++) {
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
                return;
            } catch (Exception e) {
                lastException = e;
                log.warn("AI job 제출 실패 generationId={} attempt={} reason={}", generationId, attempt + 1, e.getMessage());
            }
        }

        generation.fail();
        generationRepository.save(generation);
        log.error("AI job 제출 최종 실패 generationId={} reason={}", generationId, lastException.getMessage());
    }
}
