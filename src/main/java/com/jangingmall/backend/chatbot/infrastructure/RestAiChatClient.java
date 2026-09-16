package com.jangingmall.backend.chatbot.infrastructure;

import com.jangingmall.backend.chatbot.domain.AiChatClient;
import com.jangingmall.backend.chatbot.domain.ChatMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
class RestAiChatClient implements AiChatClient {

    private static final int MAX_HISTORY_TURNS = 6;
    private static final int MAX_RETRY_COUNT = 2;
    private static final String FALLBACK_REPLY = "현재 AI 추천을 이용할 수 없습니다";

    private final RestClient restClient;

    RestAiChatClient(
        @Value("${ai.base-url}") String baseUrl,
        @Value("${ai.timeout-seconds:30}") int timeoutSeconds
    ) {
        Duration timeout = Duration.ofSeconds(timeoutSeconds);
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
            HttpClient.newBuilder().connectTimeout(timeout).build()
        );
        factory.setReadTimeout(timeout);
        this.restClient = RestClient.builder()
            .baseUrl(baseUrl)
            .requestFactory(factory)
            .build();
    }

    @Override
    public AiChatResult chat(UUID sessionId, String message, List<ChatMessage> history) {
        Map<String, Object> body = buildRequestBody(sessionId, message, history);
        log.info("AI 챗봇 요청 sessionId={}", sessionId);

        RestClientException lastException = null;
        for (int attempt = 0; attempt <= MAX_RETRY_COUNT; attempt++) {
            try {
                AiResponse response = restClient.post()
                    .uri("/ai/chat")
                    .body(body)
                    .retrieve()
                    .body(AiResponse.class);
                return toResult(response);
            } catch (RestClientException e) {
                lastException = e;
                log.warn("AI 챗봇 호출 실패 sessionId={} attempt={} reason={}", sessionId, attempt + 1, e.getMessage());
            }
        }

        log.error("AI 챗봇 최종 실패 sessionId={} reason={}", sessionId, lastException.getMessage());
        return new AiChatResult(FALLBACK_REPLY, null, List.of(), List.of());
    }

    private Map<String, Object> buildRequestBody(UUID sessionId, String message, List<ChatMessage> history) {
        List<Map<String, String>> recentHistory = history.stream()
            .skip(Math.max(0, history.size() - MAX_HISTORY_TURNS))
            .map(m -> Map.of("sender", m.getSender().name(), "content", m.getContent()))
            .toList();
        return Map.of(
            "session_id", sessionId.toString(),
            "message", message,
            "history", recentHistory
        );
    }

    private AiChatResult toResult(AiResponse response) {
        List<ProductCard> products = response.products() == null
            ? List.of()
            : response.products().stream()
                .map(p -> new ProductCard(p.product_id(), p.reason()))
                .toList();
        return new AiChatResult(response.reply(), response.intent(), products, response.suggestions());
    }

    private record AiProductEntry(Long product_id, String reason) {}

    private record AiResponse(
        String reply,
        String intent,
        List<AiProductEntry> products,
        List<String> suggestions
    ) {}
}
