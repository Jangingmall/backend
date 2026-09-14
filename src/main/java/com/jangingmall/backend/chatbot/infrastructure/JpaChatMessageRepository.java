package com.jangingmall.backend.chatbot.infrastructure;

import com.jangingmall.backend.chatbot.domain.ChatMessage;
import com.jangingmall.backend.chatbot.domain.ChatMessageRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
interface JpaChatMessageRepositoryJpa extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findBySessionIdOrderBySentAt(UUID sessionId);
}

@Repository
class JpaChatMessageRepository implements ChatMessageRepository {

    private final JpaChatMessageRepositoryJpa jpa;

    JpaChatMessageRepository(JpaChatMessageRepositoryJpa jpa) {
        this.jpa = jpa;
    }

    @Override
    public ChatMessage save(ChatMessage message) {
        return jpa.save(message);
    }

    @Override
    public List<ChatMessage> findBySessionId(UUID sessionId) {
        return jpa.findBySessionIdOrderBySentAt(sessionId);
    }
}
