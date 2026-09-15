package com.jangingmall.backend.notification.application;

import com.jangingmall.backend.notification.infrastructure.NotificationSseEmitterRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;

@Slf4j
@Service
public class NotificationSseService {

    private static final long SSE_TIMEOUT_MILLIS = 30 * 60 * 1000L;

    private final NotificationSseEmitterRepository emitterRepository;
    private final Counter sseSentCounter;
    private final Counter sseFailedCounter;

    public NotificationSseService(NotificationSseEmitterRepository emitterRepository, MeterRegistry meterRegistry) {
        this.emitterRepository = emitterRepository;
        this.sseSentCounter = Counter.builder("sse.sent.total")
            .description("SSE 전송 성공 누적")
            .register(meterRegistry);
        this.sseFailedCounter = Counter.builder("sse.failed.total")
            .description("SSE 전송 실패 누적")
            .register(meterRegistry);
    }

    public SseEmitter subscribe(Long memberId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MILLIS);
        emitterRepository.add(memberId, emitter);

        Runnable cleanup = () -> emitterRepository.remove(memberId, emitter);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(throwable -> {
            log.warn("SSE emitter error for memberId={}: {}", memberId, throwable.getMessage());
            cleanup.run();
        });

        sendConnectEvent(memberId, emitter);
        return emitter;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onNotificationCreated(NotificationCreatedEvent event) {
        emit(event.memberId(), event.notification());
    }

    private void emit(Long memberId, NotificationResponse notification) {
        List<SseEmitter> targets = emitterRepository.findAll(memberId);
        for (SseEmitter emitter : targets) {
            try {
                emitter.send(SseEmitter.event()
                    .name("notification")
                    .data(notification));
                sseSentCounter.increment();
            } catch (IOException | IllegalStateException exception) {
                log.warn("SSE send failed for memberId={}, removing emitter: {}", memberId, exception.getMessage());
                sseFailedCounter.increment();
                emitterRepository.remove(memberId, emitter);
            }
        }
    }

    private void sendConnectEvent(Long memberId, SseEmitter emitter) {
        try {
            emitter.send(SseEmitter.event()
                .name("connect")
                .data("connected"));
        } catch (IOException | IllegalStateException exception) {
            log.warn("SSE connect event failed for memberId={}: {}", memberId, exception.getMessage());
            emitterRepository.remove(memberId, emitter);
        }
    }
}
