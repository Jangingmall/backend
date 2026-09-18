package com.jangingmall.backend.e2e;

import com.jangingmall.backend.payment.application.PaymentGateway;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Primary
@Profile("local-postgresql")
public class StubPaymentGateway implements PaymentGateway {

    private final Map<String, PaymentSnapshot> registry = new ConcurrentHashMap<>();

    public void register(String paymentKey, String orderNumber, long amount, String status) {
        registry.put(paymentKey, new PaymentSnapshot(paymentKey, orderNumber, amount, status));
    }

    public void reset() {
        registry.clear();
    }

    @Override
    public void confirm(String paymentKey, String orderNumber, long amount) {}

    @Override
    public void cancel(String paymentKey, String reason) {}

    @Override
    public PaymentSnapshot find(String paymentKey) {
        PaymentSnapshot snapshot = registry.get(paymentKey);
        if (snapshot == null) {
            throw new IllegalStateException("StubPaymentGateway: no snapshot registered for paymentKey=" + paymentKey);
        }
        return snapshot;
    }

    @Override
    public Optional<PaymentSnapshot> findByOrderNumber(String orderNumber) {
        return registry.values().stream()
            .filter(s -> orderNumber.equals(s.orderNumber()))
            .findFirst();
    }
}
