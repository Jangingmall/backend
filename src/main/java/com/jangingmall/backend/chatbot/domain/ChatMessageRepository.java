package com.jangingmall.backend.chatbot.domain;

import java.util.List;
import java.util.UUID;

public interface ChatMessageRepository {

    ChatMessage save(ChatMessage message);

    List<ChatMessage> findBySessionId(UUID sessionId);
}
