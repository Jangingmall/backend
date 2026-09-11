package com.jangingmall.backend.payment.infrastructure;

import com.jangingmall.backend.global.exception.BusinessRuleViolationException;
import com.jangingmall.backend.global.exception.ExternalServiceException;
import com.jangingmall.backend.global.exception.PaymentProviderRejectedException;
import com.jangingmall.backend.payment.application.PaymentGateway;
import com.jangingmall.backend.payment.application.PaymentProperties;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.HttpClientErrorException;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class TossPaymentsGateway implements PaymentGateway {

    private final PaymentProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    public void confirm(String paymentKey, String orderNumber, long amount) {
        post("/v1/payments/confirm", Map.of("paymentKey", paymentKey, "orderId", orderNumber, "amount", amount),
            "confirm-" + paymentKey, "결제 승인에 실패했습니다.");
    }

    @Override
    public void cancel(String paymentKey, String reason) {
        post("/v1/payments/" + paymentKey + "/cancel", Map.of("cancelReason", reason),
            "cancel-" + paymentKey, "결제 취소에 실패했습니다.");
    }

    @Override
    @SuppressWarnings("unchecked")
    public PaymentSnapshot find(String paymentKey) {
        requireSecretKey();
        try {
            Map<String, Object> response = RestClient.create(properties.getBaseUrl()).get()
                .uri(builder -> builder.pathSegment("v1", "payments", paymentKey).build())
                .header(HttpHeaders.AUTHORIZATION, "Basic " + basicToken())
                .retrieve()
                .body(Map.class);
            return snapshot(response);
        } catch (RestClientException exception) {
            throw new ExternalServiceException("토스페이먼츠 결제 정보를 조회할 수 없습니다.");
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public Optional<PaymentSnapshot> findByOrderNumber(String orderNumber) {
        requireSecretKey();
        try {
            Map<String, Object> response = RestClient.create(properties.getBaseUrl()).get()
                .uri(builder -> builder.pathSegment("v1", "payments", "orders", orderNumber).build())
                .header(HttpHeaders.AUTHORIZATION, "Basic " + basicToken())
                .retrieve()
                .body(Map.class);
            return Optional.of(snapshot(response));
        } catch (HttpClientErrorException.NotFound exception) {
            return Optional.empty();
        } catch (RestClientException exception) {
            throw new ExternalServiceException("토스페이먼츠 결제 정보를 조회할 수 없습니다.");
        }
    }

    private PaymentSnapshot snapshot(Map<String, Object> response) {
        if (response == null || response.get("paymentKey") == null || response.get("orderId") == null
            || response.get("totalAmount") == null || response.get("status") == null) {
            throw new BusinessRuleViolationException("토스페이먼츠 결제 조회 응답이 올바르지 않습니다.");
        }
        return new PaymentSnapshot(
            String.valueOf(response.get("paymentKey")),
            String.valueOf(response.get("orderId")),
            ((Number) response.get("totalAmount")).longValue(),
            String.valueOf(response.get("status"))
        );
    }

    private void post(String path, Map<String, ?> body, String idempotencyKey, String errorMessage) {
        requireSecretKey();
        try {
            RestClient.create(properties.getBaseUrl()).post()
                .uri(path)
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Basic " + basicToken())
                .header("Idempotency-Key", idempotencyKey)
                .body(body)
                .retrieve()
                .toBodilessEntity();
        } catch (HttpClientErrorException exception) {
            ProviderError providerError = providerError(exception, errorMessage);
            throw new PaymentProviderRejectedException(providerError.code(), providerError.message());
        } catch (RestClientException exception) {
            throw new ExternalServiceException(errorMessage);
        }
    }

    private void requireSecretKey() {
        if (properties.getSecretKey() == null || properties.getSecretKey().isBlank()) {
            throw new BusinessRuleViolationException("토스페이먼츠 시크릿 키가 설정되지 않았습니다.");
        }
    }

    private String basicToken() {
        return Base64.getEncoder().encodeToString((properties.getSecretKey() + ":").getBytes(StandardCharsets.UTF_8));
    }

    @SuppressWarnings("unchecked")
    private ProviderError providerError(HttpClientErrorException exception, String fallbackMessage) {
        try {
            Map<String, Object> body = objectMapper.readValue(exception.getResponseBodyAsString(), Map.class);
            String code = body.get("code") == null ? "TOSS_REJECTED" : String.valueOf(body.get("code"));
            String message = body.get("message") == null ? fallbackMessage : String.valueOf(body.get("message"));
            return new ProviderError(code, message);
        } catch (Exception ignored) {
            return new ProviderError("TOSS_REJECTED", fallbackMessage);
        }
    }

    private record ProviderError(String code, String message) {}
}
