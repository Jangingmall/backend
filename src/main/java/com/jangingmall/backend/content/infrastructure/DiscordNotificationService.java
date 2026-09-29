package com.jangingmall.backend.content.infrastructure;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class DiscordNotificationService {

    private static final String DISCORD_USERNAME = "미담 AI 알림";
    private static final int EMBED_COLOR_INFO = 0x5865F2;
    private static final int EMBED_COLOR_SUCCESS = 0x57F287;
    private static final int EMBED_COLOR_FAILURE = 0xED4245;

    private final RestClient restClient;
    private final String webhookUrl;
    private final boolean enabled;

    DiscordNotificationService(
        @Value("${discord.notification.webhook-url:}") String webhookUrl
    ) {
        this.webhookUrl = webhookUrl;
        this.enabled = webhookUrl != null && !webhookUrl.isBlank();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
            HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5))
                .build()
        );
        factory.setReadTimeout(Duration.ofSeconds(5));
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    DiscordNotificationService(RestClient restClient, String webhookUrl) {
        this.restClient = restClient;
        this.webhookUrl = webhookUrl;
        this.enabled = webhookUrl != null && !webhookUrl.isBlank();
    }

    public void notifyGenerationRequested(Long generationId, Long productId, String productName, int imageCount) {
        String message = String.format(
            "AI 상세페이지 생성 요청\n" +
            "generationId: `%d` | productId: `%d`\n" +
            "상품명: **%s** | 이미지 수: %d",
            generationId, productId, productName, imageCount
        );
        log.info("[Discord] AI 생성 요청 generationId={} productId={} productName={} imageCount={}",
            generationId, productId, productName, imageCount);
        send(message, EMBED_COLOR_INFO);
    }

    public void notifyGenerationSucceeded(Long generationId, Long productId, String jobId) {
        String message = String.format(
            "AI job 제출 완료\n" +
            "generationId: `%d` | productId: `%d`\n" +
            "jobId: `%s`",
            generationId, productId, jobId
        );
        log.info("[Discord] AI job 제출 완료 generationId={} jobId={}", generationId, jobId);
        send(message, EMBED_COLOR_SUCCESS);
    }

    public void notifyGenerationFailed(Long generationId, Long productId, String reason) {
        String message = String.format(
            "AI job 제출 최종 실패\n" +
            "generationId: `%d` | productId: `%d`\n" +
            "사유: %s",
            generationId, productId, reason
        );
        log.warn("[Discord] AI job 제출 실패 generationId={} productId={} reason={}", generationId, productId, reason);
        send(message, EMBED_COLOR_FAILURE);
    }

    private void send(String description, int color) {
        if (!enabled) {
            return;
        }
        Map<String, Object> embed = Map.of(
            "description", description,
            "color", color
        );
        Map<String, Object> payload = Map.of(
            "username", DISCORD_USERNAME,
            "embeds", List.of(embed)
        );
        try {
            restClient.post()
                .uri(webhookUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .toBodilessEntity();
        } catch (RestClientException e) {
            log.warn("[Discord] 웹훅 전송 실패 reason={}", e.getMessage());
        }
    }
}
