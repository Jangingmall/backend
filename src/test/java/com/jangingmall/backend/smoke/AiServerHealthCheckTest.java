package com.jangingmall.backend.smoke;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AI 서버 헬스체크 — 클러스터 내부에서만 도달 가능한 AI_BASE_URL을 검증한다.
 * 사용: ./gradlew aiHealthCheck -Pai.base-url=http://ai.internal:8001
 */
@Tag("ai-health")
class AiServerHealthCheckTest {

    @Test
    @DisplayName("AI 서버 GET /ai/health 가 200 OK와 status:ok를 반환한다")
    void aiServerHealth() throws Exception {
        String baseUrl = System.getProperty("ai.base-url",
            System.getenv().getOrDefault("AI_BASE_URL", "http://localhost:8001"));

        HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/ai/health"))
            .timeout(Duration.ofSeconds(10))
            .GET()
            .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode())
            .as("AI 서버 /ai/health HTTP 상태 코드")
            .isEqualTo(200);
        assertThat(response.body())
            .as("AI 서버 응답 바디")
            .contains("\"status\"")
            .contains("ok");
    }
}
