package com.jangingmall.backend.chatbot.infrastructure;

import com.jangingmall.backend.chatbot.domain.AiChatClient;
import com.jangingmall.backend.chatbot.domain.ChatMessage;
import com.jangingmall.backend.chatbot.domain.ChatSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
class RestAiChatClient implements AiChatClient {

    private static final int MAX_HISTORY_TURNS = 6;

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
        List<Map<String, String>> recentHistory = history.stream()
            .skip(Math.max(0, history.size() - MAX_HISTORY_TURNS))
            .map(m -> Map.of("sender", m.getSender().name(), "content", m.getContent()))
            .toList();

        Map<String, Object> body = Map.of(
            "session_id", sessionId.toString(),
            "message", message,
            "history", recentHistory
        );

        log.info("AI 챗봇 요청 sessionId={}", sessionId);

        AiResponse response = restClient.post()
            .uri("/ai/chat")
            .body(body)
            .retrieve()
            .body(AiResponse.class);

        return new AiChatResult(
            response.reply(),
            response.intent(),
            response.product_ids(),
            response.suggestions()
        );
    }

    private record AiResponse(
        String reply,
        String intent,
        List<Long> product_ids,
        List<String> suggestions
    ) {}
}
