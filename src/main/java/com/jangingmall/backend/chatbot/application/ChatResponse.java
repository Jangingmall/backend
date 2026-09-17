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
        int expiresInSeconds
    ) implements ChatResponse {

        public static SessionView from(ChatSession session) {
            return new SessionView(session.getSessionId(), 3600);
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

    record ThumbnailVariant(
        String url,
        int width,
        int height,
        String format
    ) {}

    record ProductCard(
        Long productId,
        String name,
        int price,
        List<ThumbnailVariant> thumbnail,
        String status,
        String category,
        String subcategory,
        String color,
        String giftTheme,
        Double rating,
        boolean isLimited,
        boolean isCustomOrder,
        boolean isSingleItem,
        boolean isNew,
        boolean hasGiftWrap,
        boolean hasOptions,
        List<String> purposeTags,
        String primaryBadge,
        Long artisanId,
        String artisanName,
        String reason
    ) implements ChatResponse {}

    record SendResult(
        UUID sessionId,
        Long messageId,
        String reply,
        String intent,
        List<String> suggestions,
        List<ProductCard> products
    ) implements ChatResponse {}
}
