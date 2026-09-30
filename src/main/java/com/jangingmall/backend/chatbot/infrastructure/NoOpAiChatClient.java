package com.jangingmall.backend.chatbot.infrastructure;

import com.jangingmall.backend.chatbot.domain.AiChatClient;
import com.jangingmall.backend.chatbot.domain.ChatMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@ConditionalOnMissingBean(AiChatClient.class)
class NoOpAiChatClient implements AiChatClient {

    @Override
    public AiChatResult chat(UUID sessionId, String message, List<ChatMessage> history) {
        log.warn("AI_BASE_URL 미설정 — chat skip sessionId={}", sessionId);
        return new AiChatResult(BACKEND_FALLBACK_REPLY, null, List.of(), List.of());
    }
}
