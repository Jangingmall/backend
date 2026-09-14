package com.jangingmall.backend.chatbot.infrastructure;

import com.jangingmall.backend.chatbot.domain.ChatSession;
import com.jangingmall.backend.chatbot.domain.ChatSessionRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
interface JpaChatSessionRepositoryJpa extends JpaRepository<ChatSession, UUID> {}

@Repository
class JpaChatSessionRepository implements ChatSessionRepository {

    private final JpaChatSessionRepositoryJpa jpa;

    JpaChatSessionRepository(JpaChatSessionRepositoryJpa jpa) {
        this.jpa = jpa;
    }

    @Override
    public ChatSession save(ChatSession session) {
        return jpa.save(session);
    }

    @Override
    public Optional<ChatSession> findById(UUID sessionId) {
        return jpa.findById(sessionId);
    }
}
