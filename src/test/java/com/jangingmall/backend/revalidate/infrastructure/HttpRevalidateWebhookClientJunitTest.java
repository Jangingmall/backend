package com.jangingmall.backend.revalidate.infrastructure;

import com.jangingmall.backend.revalidate.application.RevalidateProperties;
import com.jangingmall.backend.revalidate.domain.RevalidateEvent;
import com.jangingmall.backend.revalidate.domain.RevalidateEventType;
import com.jangingmall.backend.revalidate.infrastructure.HttpRevalidateWebhookClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;

class HttpRevalidateWebhookClientJunitTest {

    private static final String WEBHOOK_URL = "https://stg.midam.store/api/revalidate";
    private static final String SECRET = "test-secret-key";
    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder().findAndAddModules().build();

    private MockRestServiceServer mockServer;
    private HttpRevalidateWebhookClient client;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        mockServer = MockRestServiceServer.bindTo(restTemplate).build();
        RestClient restClient = RestClient.builder(restTemplate).build();
        RevalidateProperties properties = new RevalidateProperties(WEBHOOK_URL, SECRET, 10);
        client = new HttpRevalidateWebhookClient(restClient, properties, OBJECT_MAPPER);
    }

    @Test
    @DisplayName("상품 이벤트 발송 시 HMAC 서명과 body가 동일한 바이트로 전송된다")
    void send_product_event_signature_matches_body() {
        RevalidateEvent event = RevalidateEvent.ofProduct(
            RevalidateEventType.PRODUCT_CREATED,
            UUID.randomUUID().toString(),
            Instant.parse("2026-09-23T10:00:00Z"),
            42L
        );

        mockServer.expect(requestTo(WEBHOOK_URL))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("Content-Type", MediaType.APPLICATION_JSON_VALUE))
            .andExpect(request -> {
                String timestampHeader = request.getHeaders().getFirst("X-Revalidate-Timestamp");
                String signatureHeader = request.getHeaders().getFirst("X-Revalidate-Signature");
                String bodyStr = request.getBody().toString();
                assertThat(timestampHeader).isNotNull();
                assertThat(signatureHeader).startsWith("sha256=");
                String expectedSignature = "sha256=" + computeHmac(SECRET, timestampHeader + "." + bodyStr);
                assertThat(signatureHeader).isEqualTo(expectedSignature);
                assertThat(bodyStr).contains("product.created");
                assertThat(bodyStr).contains("42");
            })
            .andRespond(withNoContent());

        client.send(event);
        mockServer.verify();
    }

    @Test
    @DisplayName("작가 이벤트 발송 시 data에 artisanId가 포함된다")
    void send_artisan_event_body_contains_artisan_id() {
        RevalidateEvent event = RevalidateEvent.ofArtisan(
            RevalidateEventType.ARTISAN_UPDATED,
            UUID.randomUUID().toString(),
            Instant.parse("2026-09-23T10:00:00Z"),
            99L
        );

        mockServer.expect(requestTo(WEBHOOK_URL))
            .andExpect(method(HttpMethod.POST))
            .andExpect(request -> {
                String bodyStr = request.getBody().toString();
                assertThat(bodyStr).contains("artisan.updated");
                assertThat(bodyStr).contains("99");
                assertThat(bodyStr).doesNotContain("productId");
            })
            .andRespond(withNoContent());

        client.send(event);
        mockServer.verify();
    }

    @Test
    @DisplayName("서버 에러 발생 시 최대 3회(초기+2회 재시도) 요청 후 로그만 남기고 예외를 삼키지 않는다")
    void send_retries_on_server_error_and_does_not_throw() {
        RevalidateEvent event = RevalidateEvent.ofProduct(
            RevalidateEventType.PRODUCT_UPDATED,
            UUID.randomUUID().toString(),
            Instant.parse("2026-09-23T10:00:00Z"),
            1L
        );

        mockServer.expect(requestTo(WEBHOOK_URL)).andExpect(method(HttpMethod.POST)).andRespond(withServerError());
        mockServer.expect(requestTo(WEBHOOK_URL)).andExpect(method(HttpMethod.POST)).andRespond(withServerError());
        mockServer.expect(requestTo(WEBHOOK_URL)).andExpect(method(HttpMethod.POST)).andRespond(withServerError());

        // 예외가 전파되지 않아야 한다
        client.send(event);
        mockServer.verify();
    }

    @Test
    @DisplayName("재시도 시 eventId는 동일하고 timestamp는 새로 생성된다")
    void send_retry_uses_same_event_id_different_timestamp() {
        String fixedEventId = "fixed-event-id-1234";
        RevalidateEvent event = RevalidateEvent.ofProduct(
            RevalidateEventType.PRODUCT_DELETED,
            fixedEventId,
            Instant.parse("2026-09-23T10:00:00Z"),
            7L
        );

        String[] capturedTimestamps = new String[2];
        String[] capturedEventIds = new String[2];
        int[] callCount = {0};

        mockServer.expect(requestTo(WEBHOOK_URL))
            .andExpect(method(HttpMethod.POST))
            .andExpect(request -> {
                capturedTimestamps[callCount[0]] = request.getHeaders().getFirst("X-Revalidate-Timestamp");
                String bodyStr = request.getBody().toString();
                assertThat(bodyStr).contains(fixedEventId);
                capturedEventIds[callCount[0]] = fixedEventId;
                callCount[0]++;
            })
            .andRespond(withServerError());
        mockServer.expect(requestTo(WEBHOOK_URL))
            .andExpect(method(HttpMethod.POST))
            .andExpect(request -> {
                capturedTimestamps[callCount[0]] = request.getHeaders().getFirst("X-Revalidate-Timestamp");
                String bodyStr = request.getBody().toString();
                assertThat(bodyStr).contains(fixedEventId);
                capturedEventIds[callCount[0]] = fixedEventId;
                callCount[0]++;
            })
            .andRespond(withNoContent());

        client.send(event);
        mockServer.verify();

        assertThat(capturedEventIds[0]).isEqualTo(capturedEventIds[1]);
    }

    private static String computeHmac(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
