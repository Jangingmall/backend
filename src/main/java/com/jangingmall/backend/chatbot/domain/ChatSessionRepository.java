package com.jangingmall.backend.chatbot.domain;

import java.util.Optional;
import java.util.UUID;

public interface ChatSessionRepository {

    ChatSession save(ChatSession session);

    Optional<ChatSession> findById(UUID sessionId);
}
