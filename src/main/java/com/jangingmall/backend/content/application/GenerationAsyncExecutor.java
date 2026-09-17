package com.jangingmall.backend.content.application;

import com.jangingmall.backend.content.domain.AiContentClient;
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
    private final ContentService contentService;

    // @TransactionalEventListener(AFTER_COMMIT)이 트랜잭션 외부에서 실행되므로
    // request()가 커밋된 뒤에야 row가 보임 — @Async 자기 호출 레이스 조건 해소
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
                String reactDocumentJson = aiContentClient.requestGeneration(
                    generationId,
                    command.productId(),
                    command.images(),
                    command.productName(),
                    command.howMade(),
                    command.careTips()
                );
                generation.complete(reactDocumentJson, generationId.toString());
                generationRepository.save(generation);
                contentService.storeReactDocument(
                    new ContentCommand.StoreReactDocument(command.productId(), reactDocumentJson, command.requesterId())
                );
                log.info("AI 콘텐츠 생성 완료 generationId={}", generationId);
                return;
            } catch (Exception e) {
                lastException = e;
                log.warn("AI 콘텐츠 생성 실패 generationId={} attempt={} reason={}", generationId, attempt + 1, e.getMessage());
            }
        }

        generation.fail();
        generationRepository.save(generation);
        log.error("AI 콘텐츠 생성 최종 실패 generationId={} reason={}", generationId, lastException.getMessage());
    }
}
