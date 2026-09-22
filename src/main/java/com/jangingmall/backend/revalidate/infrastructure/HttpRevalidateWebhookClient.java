package com.jangingmall.backend.revalidate.infrastructure;

import com.jangingmall.backend.revalidate.application.RevalidateProperties;
import com.jangingmall.backend.revalidate.domain.RevalidateEvent;
import com.jangingmall.backend.revalidate.domain.RevalidateWebhookClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Component
public class HttpRevalidateWebhookClient implements RevalidateWebhookClient {

    private static final int MAX_RETRY_COUNT = 2;
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final RestClient restClient;
    private final RevalidateProperties properties;
    private final ObjectMapper objectMapper;

    HttpRevalidateWebhookClient(RevalidateProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        Duration timeout = Duration.ofSeconds(properties.timeoutSeconds());
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
            HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(timeout).build()
        );
        factory.setReadTimeout(timeout);
        this.restClient = RestClient.builder()
            .requestFactory(factory)
            .build();
    }

    HttpRevalidateWebhookClient(RestClient restClient, RevalidateProperties properties, ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public void send(RevalidateEvent event) {
        String bodyJson = serializeBody(event);
        RestClientException lastException = null;

        for (int attempt = 0; attempt <= MAX_RETRY_COUNT; attempt++) {
            long timestamp = Instant.now().getEpochSecond();
            String signature = sign(timestamp, bodyJson);
            try {
                restClient.post()
                    .uri(properties.webhookUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Revalidate-Timestamp", String.valueOf(timestamp))
                    .header("X-Revalidate-Signature", "sha256=" + signature)
                    .body(bodyJson)
                    .retrieve()
                    .toBodilessEntity();
                log.info("revalidate webhook sent eventType={} eventId={} attempt={}", event.eventType().value(), event.eventId(), attempt);
                return;
            } catch (RestClientException e) {
                lastException = e;
                log.warn("revalidate webhook attempt={} failed eventType={} eventId={} reason={}", attempt, event.eventType().value(), event.eventId(), e.getMessage());
            }
        }
        log.error("revalidate webhook exhausted retries eventType={} eventId={} reason={}", event.eventType().value(), event.eventId(), lastException != null ? lastException.getMessage() : "unknown");
    }

    private String serializeBody(RevalidateEvent event) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("event", event.eventType().value());
            body.put("eventId", event.eventId());
            body.put("occurredAt", event.occurredAt().toString());
            Map<String, Object> data = new LinkedHashMap<>();
            if (event.productId() != null) {
                data.put("productId", event.productId());
            }
            if (event.artisanId() != null) {
                data.put("artisanId", event.artisanId());
            }
            body.put("data", data);
            return objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            throw new RevalidateSerializationException("revalidate body 직렬화 실패", e);
        }
    }

    private String sign(long timestamp, String bodyJson) {
        try {
            String signingInput = timestamp + "." + bodyJson;
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(properties.webhookSecret().getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            byte[] hmacBytes = mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hmacBytes);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RevalidateSigningException("HMAC-SHA256 서명 실패", e);
        }
    }
}
