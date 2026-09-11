package com.jangingmall.backend.payment.application;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderExpirationScheduler {

    private final PaymentService payments;
    private final PaymentProperties properties;

    @Scheduled(fixedDelayString = "${toss.payments.expiration-scan-millis:60000}")
    public void expireAbandonedOrders() {
        Instant cutoff = Instant.now().minusSeconds(properties.getOrderExpirationSeconds());
        payments.expireCreatedOrders(cutoff);
    }
}
