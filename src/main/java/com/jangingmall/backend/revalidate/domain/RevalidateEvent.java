package com.jangingmall.backend.revalidate.domain;

import java.time.Instant;

/**
 * Spring ApplicationEvent — 트랜잭션 커밋 후 @TransactionalEventListener(AFTER_COMMIT)으로 소비된다.
 * eventId는 재시도 시에도 동일 값을 유지한다.
 */
public record RevalidateEvent(
    RevalidateEventType eventType,
    String eventId,
    Instant occurredAt,
    Long productId,
    Long artisanId
) {
    public static RevalidateEvent ofProduct(RevalidateEventType eventType, String eventId, Instant occurredAt, Long productId) {
        return new RevalidateEvent(eventType, eventId, occurredAt, productId, null);
    }

    public static RevalidateEvent ofArtisan(RevalidateEventType eventType, String eventId, Instant occurredAt, Long artisanId) {
        return new RevalidateEvent(eventType, eventId, occurredAt, null, artisanId);
    }
}
