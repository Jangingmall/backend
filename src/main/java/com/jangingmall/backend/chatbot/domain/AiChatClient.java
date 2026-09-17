package com.jangingmall.backend.chatbot.domain;

import java.util.List;
import java.util.UUID;

public interface AiChatClient {

    AiChatResult chat(UUID sessionId, String message, List<ChatMessage> history);

    record ProductCard(Long productId, String reason) {}

    record AiChatResult(
        String reply,
        String intent,
        List<ProductCard> products,
        List<String> suggestions
    ) {}
}
