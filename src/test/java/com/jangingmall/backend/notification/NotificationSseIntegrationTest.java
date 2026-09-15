package com.jangingmall.backend.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jangingmall.backend.global.security.JwtProperties;
import com.jangingmall.backend.global.security.JwtTokenProvider;
import com.jangingmall.backend.member.domain.MemberRole;
import com.jangingmall.backend.notification.application.NotificationCreateRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("local-h2")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "management.server.port=-1")
class NotificationSseIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private JwtProperties jwtProperties;

    private JwtTokenProvider jwtTokenProvider;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private String token;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider(jwtProperties);
        token = jwtTokenProvider.createAccessToken(1L, MemberRole.USER);
    }

    private HttpClient newHttpClient() {
        return HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .version(HttpClient.Version.HTTP_1_1)
            .build();
    }

    @Test
    @DisplayName("SSE 연결 시 200 응답과 text/event-stream 콘텐츠 타입을 반환한다")
    void stream_returnsOkWithEventStreamContentType() throws Exception {
        HttpClient client = newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/api/notifications/stream?token=" + token))
            .header(HttpHeaders.ACCEPT, MediaType.TEXT_EVENT_STREAM_VALUE)
            .GET()
            .build();

        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue(HttpHeaders.CONTENT_TYPE).orElse(""))
            .contains(MediaType.TEXT_EVENT_STREAM_VALUE);

        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body()))) {
            String line;
            long deadline = System.currentTimeMillis() + 3000;
            while ((line = reader.readLine()) != null && System.currentTimeMillis() < deadline) {
                lines.add(line);
                if (line.contains("connect")) {
                    break;
                }
            }
        }

        assertThat(lines.stream().anyMatch(l -> l.contains("connect"))).isTrue();
    }

    @Test
    @DisplayName("토큰 없이 SSE 연결 시 401을 반환한다")
    void stream_withoutToken_returns401() throws Exception {
        HttpClient client = newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/api/notifications/stream"))
            .header(HttpHeaders.ACCEPT, MediaType.TEXT_EVENT_STREAM_VALUE)
            .timeout(Duration.ofSeconds(5))
            .GET()
            .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(401);
    }

    @Test
    @DisplayName("SSE 구독 후 알림 생성 시 notification 이벤트가 수신된다")
    void stream_receivesNotificationEventAfterCreate() throws Exception {
        HttpClient sseClient = newHttpClient();
        HttpClient postClient = newHttpClient();

        HttpRequest sseRequest = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/api/notifications/stream?token=" + token))
            .header(HttpHeaders.ACCEPT, MediaType.TEXT_EVENT_STREAM_VALUE)
            .GET()
            .build();

        HttpResponse<InputStream> sseResponse = sseClient.send(sseRequest, HttpResponse.BodyHandlers.ofInputStream());
        assertThat(sseResponse.statusCode()).isEqualTo(200);

        List<String> receivedLines = new ArrayList<>();
        CountDownLatch connectLatch = new CountDownLatch(1);
        CountDownLatch notificationLatch = new CountDownLatch(1);
        List<Exception> errors = new ArrayList<>();

        Thread sseThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(sseResponse.body()))) {
                String line;
                boolean notificationEventSeen = false;
                while ((line = reader.readLine()) != null) {
                    if (!line.isEmpty()) {
                        receivedLines.add(line);
                    }
                    if (line.contains("event:connect")) {
                        connectLatch.countDown();
                    }
                    if (line.startsWith("event:notification")) {
                        notificationEventSeen = true;
                    }
                    if (notificationEventSeen && line.startsWith("data:")) {
                        notificationLatch.countDown();
                        break;
                    }
                }
            } catch (Exception e) {
                errors.add(e);
                connectLatch.countDown();
                notificationLatch.countDown();
            }
        }, "sse-reader");
        sseThread.setDaemon(true);
        sseThread.start();

        boolean connectArrived = connectLatch.await(5, TimeUnit.SECONDS);
        assertThat(errors).isEmpty();
        assertThat(connectArrived).as("SSE connect event must arrive within 5 seconds").isTrue();

        NotificationCreateRequest createRequest = new NotificationCreateRequest("주문 완료", "청자 상감 다완 주문이 접수되었습니다.");
        String body = objectMapper.writeValueAsString(createRequest);

        HttpRequest postRequest = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/api/notifications"))
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .timeout(Duration.ofSeconds(5))
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();

        HttpResponse<String> postResponse = postClient.send(postRequest, HttpResponse.BodyHandlers.ofString());
        assertThat(postResponse.statusCode()).isEqualTo(200);

        boolean notificationArrived = notificationLatch.await(5, TimeUnit.SECONDS);
        assertThat(errors).isEmpty();
        assertThat(notificationArrived).as("SSE notification event must arrive within 5 seconds after POST").isTrue();
        assertThat(receivedLines.stream().anyMatch(l -> l.contains("notification"))).isTrue();
        assertThat(receivedLines.stream().anyMatch(l -> l.contains("주문 완료"))).isTrue();
    }
}
