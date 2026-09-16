package com.jangingmall.backend.chatbot.application;

import com.jangingmall.backend.chatbot.domain.ChatMessage;
import com.jangingmall.backend.chatbot.domain.ChatSender;
import com.jangingmall.backend.chatbot.domain.ChatSession;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public sealed interface ChatResponse {

    record SessionView(
        UUID sessionId,
        Long memberId,
        LocalDateTime createdAt,
        LocalDateTime endedAt
    ) implements ChatResponse {

        public static SessionView from(ChatSession session) {
            return new SessionView(
                session.getSessionId(),
                session.getMemberId(),
                session.getCreatedAt(),
                session.getEndedAt()
            );
        }
    }

    record MessageView(
        Long messageId,
        UUID sessionId,
        ChatSender sender,
        String content,
        LocalDateTime sentAt
    ) implements ChatResponse {

        public static MessageView from(ChatMessage message) {
            return new MessageView(
                message.getMessageId(),
                message.getSessionId(),
                message.getSender(),
                message.getContent(),
                message.getSentAt()
            );
        }
    }

    record ProductCard(
        Long productId,
        String productName,
        String thumbnailUrl,
        int price,
        String reason
    ) implements ChatResponse {}

    record SendResult(
        MessageView userMessage,
        MessageView botMessage,
        List<ProductCard> recommendedProducts,
        List<String> suggestions
    ) implements ChatResponse {}
}
