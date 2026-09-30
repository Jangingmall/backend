package com.jangingmall.backend.chatbot.infrastructure;

import com.jangingmall.backend.chatbot.domain.AiChatClient;
import com.jangingmall.backend.chatbot.domain.ChatMessage;
import com.jangingmall.backend.chatbot.domain.ChatSender;
import lombok.extern.slf4j.Slf4j;
import com.jangingmall.backend.global.config.AiProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@ConditionalOnProperty(name = "ai.base-url", matchIfMissing = false)
class RestAiChatClient implements AiChatClient {

    private static final int MAX_HISTORY_TURNS = 6;
    private static final int MAX_RETRY_COUNT = 2;
    private static final String FALLBACK_REPLY = BACKEND_FALLBACK_REPLY;

    private final RestClient restClient;

    @Autowired
    RestAiChatClient(AiProperties aiProperties) {
        Duration timeout = Duration.ofSeconds(aiProperties.timeoutSeconds());
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
            HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(timeout).build()
        );
        factory.setReadTimeout(timeout);
        this.restClient = RestClient.builder()
            .baseUrl(aiProperties.baseUrl())
            .requestFactory(factory)
            .build();
    }

    RestAiChatClient(RestClient restClient) {
        this.restClient = restClient;
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
                if (isReadTimeout(e)) {
                    // 연결은 됐지만 응답이 늦은 경우다. 챗봇은 끊긴 요청도 계속 처리하므로 재시도하면 부하와 대기만 배로 는다.
                    log.warn("AI 챗봇 응답 지연 — 재시도하지 않음 sessionId={}", sessionId);
                    break;
                }
            }
        }

        log.error("AI 챗봇 최종 실패 sessionId={} reason={}", sessionId, lastException.getMessage());
        return new AiChatResult(FALLBACK_REPLY, null, List.of(), List.of());
    }

    /** 요청이 전송된 뒤 응답 대기 시간이 초과됐는지(연결 시간 초과는 제외). */
    static boolean isReadTimeout(RestClientException e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof HttpConnectTimeoutException) {
                return false;
            }
            if (cause instanceof HttpTimeoutException) {
                return true;
            }
        }
        return false;
    }

    private Map<String, Object> buildRequestBody(UUID sessionId, String message, List<ChatMessage> history) {
        List<ChatMessage> usable = history.stream()
            .filter(m -> !(m.getSender() == ChatSender.ADMIN && FALLBACK_REPLIES.contains(m.getContent())))
            .toList();
        List<Map<String, String>> recentHistory = usable.stream()
            .skip(Math.max(0, usable.size() - MAX_HISTORY_TURNS))
            .map(m -> Map.of("sender", m.getSender().name(), "content", m.getContent()))
            .toList();
        return Map.of(
            "session_id", sessionId.toString(),
            "message", message,
            "history", recentHistory
        );
    }

    private AiChatResult toResult(AiResponse response) {
        List<ProductCard> products = response.product_ids() == null
            ? List.of()
            : response.product_ids().stream()
                .map(id -> new ProductCard(id, null))
                .toList();
        return new AiChatResult(response.reply(), response.intent(), products, response.suggestions());
    }

    private record AiResponse(
        String reply,
        String intent,
        List<Long> product_ids,
        List<String> suggestions
    ) {}
}
