package com.jangingmall.backend.revalidate.application;

import com.jangingmall.backend.revalidate.domain.RevalidateEvent;
import com.jangingmall.backend.revalidate.domain.RevalidateWebhookClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class RevalidateWebhookDispatcher {

    private final RevalidateWebhookClient revalidateWebhookClient;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRevalidateEvent(RevalidateEvent event) {
        log.info("revalidate webhook dispatch start eventType={} eventId={}", event.eventType().value(), event.eventId());
        revalidateWebhookClient.send(event);
    }
}
