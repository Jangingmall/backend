package com.jangingmall.backend.chatbot.application;

import com.jangingmall.backend.chatbot.domain.AiChatClient;
import com.jangingmall.backend.chatbot.domain.AiChatClient.AiChatResult;
import com.jangingmall.backend.chatbot.domain.ChatErrorMessage;
import com.jangingmall.backend.chatbot.domain.ChatMessage;
import com.jangingmall.backend.chatbot.domain.ChatMessageRepository;
import com.jangingmall.backend.chatbot.domain.ChatSender;
import com.jangingmall.backend.chatbot.domain.ChatSession;
import com.jangingmall.backend.chatbot.domain.ChatSessionRepository;
import com.jangingmall.backend.global.exception.BusinessRuleViolationException;
import com.jangingmall.backend.global.exception.ForbiddenException;
import com.jangingmall.backend.global.exception.NotFoundException;
import com.jangingmall.backend.member.domain.ArtisanProfile;
import com.jangingmall.backend.member.domain.ArtisanProfileRepository;
import com.jangingmall.backend.product.domain.Product;
import com.jangingmall.backend.product.domain.ProductRepository;
import com.jangingmall.backend.product.domain.ProductReviewRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final AiChatClient aiChatClient;
    private final ProductRepository productRepository;
    private final ProductReviewRepository reviewRepository;
    private final ArtisanProfileRepository artisanProfileRepository;

    @Transactional
    public ChatResponse.SessionView createSession(ChatCommand.CreateSession command) {
        return ChatResponse.SessionView.from(sessionRepository.save(ChatSession.create(command.memberId())));
    }

    @Transactional
    public ChatResponse.SendResult sendMessage(ChatCommand.SendMessage command) {
        ChatSession session = getSession(command.sessionId());
        verifyOwner(session, command.memberId());
        if (session.isEnded()) {
            throw new BusinessRuleViolationException(ChatErrorMessage.SESSION_ALREADY_ENDED.message());
        }

        messageRepository.save(ChatMessage.of(command.sessionId(), ChatSender.USER, command.content()));

        List<ChatMessage> history = messageRepository.findBySessionId(command.sessionId());
        AiChatResult result = aiChatClient.chat(command.sessionId(), command.content(), history);

        ChatMessage botMessage = messageRepository.save(
            ChatMessage.of(command.sessionId(), ChatSender.ADMIN, result.reply())
        );

        List<ChatResponse.ProductCard> productCards = assembleProductCards(result.products());

        return new ChatResponse.SendResult(
            command.sessionId(),
            botMessage.getMessageId(),
            result.reply(),
            result.intent(),
            result.suggestions(),
            productCards
        );
    }

    @Transactional(readOnly = true)
    public List<ChatResponse.MessageView> findMessages(UUID sessionId, Long memberId) {
        ChatSession session = getSession(sessionId);
        verifyOwner(session, memberId);
        return messageRepository.findBySessionId(sessionId)
            .stream()
            .map(ChatResponse.MessageView::from)
            .toList();
    }

    @Transactional
    public void endSession(ChatCommand.EndSession command) {
        ChatSession session = getSession(command.sessionId());
        verifyOwner(session, command.memberId());
        if (session.isEnded()) {
            throw new BusinessRuleViolationException(ChatErrorMessage.SESSION_ALREADY_ENDED.message());
        }
        session.end();
    }

    private List<ChatResponse.ProductCard> assembleProductCards(List<AiChatClient.ProductCard> aiCards) {
        return aiCards.stream()
            .map(this::toProductCard)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .toList();
    }

    private Optional<ChatResponse.ProductCard> toProductCard(AiChatClient.ProductCard card) {
        Optional<Product> found = productRepository.findById(card.productId());
        if (found.isEmpty()) {
            log.warn("AI 추천 상품 미존재 productId={}", card.productId());
            return Optional.empty();
        }
        Product product = found.get();

        Optional<ArtisanProfile> artisan = artisanProfileRepository.findById(product.getArtisanId());
        String artisanName = artisan.map(ArtisanProfile::getBusinessName).orElse("");

        Double rating = reviewRepository.findAverageRatingByProductId(product.getId());

        List<ChatResponse.ThumbnailVariant> thumbnail = buildThumbnailVariants(product.getThumbnailUrl());

        String categoryCode = product.getCategory() != null ? product.getCategory().getName() : null;
        String subcategoryCode = product.getSubcategory() != null ? product.getSubcategory().getName() : null;
        String color = product.getColors().isEmpty() ? null : product.getColors().get(0);
        String giftTheme = product.getGiftThemes().isEmpty() ? null : product.getGiftThemes().get(0);

        LocalDateTime createdAt = product.getCreatedAt();
        boolean isNew = createdAt != null && createdAt.isAfter(LocalDateTime.now().minusDays(7));
        String primaryBadge = computePrimaryBadge(product.isLimited(), isNew);

        return Optional.of(new ChatResponse.ProductCard(
            product.getId(),
            product.getTitle(),
            product.getPrice(),
            thumbnail,
            product.getStatus().name(),
            categoryCode,
            subcategoryCode,
            color,
            giftTheme,
            rating,
            product.isLimited(),
            product.isCustomOrder(),
            product.isSingleItem(),
            isNew,
            product.isHasGiftWrap(),
            false,
            product.getPurposeTags(),
            primaryBadge,
            product.getArtisanId(),
            artisanName,
            card.reason()
        ));
    }

    private List<ChatResponse.ThumbnailVariant> buildThumbnailVariants(String thumbnailUrl) {
        if (thumbnailUrl == null) {
            return List.of();
        }
        return List.of(
            new ChatResponse.ThumbnailVariant(thumbnailUrl, 320, 320, "webp"),
            new ChatResponse.ThumbnailVariant(thumbnailUrl, 640, 640, "webp"),
            new ChatResponse.ThumbnailVariant(thumbnailUrl, 1280, 1280, "webp")
        );
    }

    private String computePrimaryBadge(boolean isLimited, boolean isNew) {
        if (isLimited) {
            return "LIMITED";
        }
        if (isNew) {
            return "NEW";
        }
        return null;
    }

    private ChatSession getSession(UUID sessionId) {
        return sessionRepository.findById(sessionId)
            .orElseThrow(() -> new NotFoundException(ChatErrorMessage.SESSION_NOT_FOUND.message()));
    }

    private void verifyOwner(ChatSession session, Long memberId) {
        if (!session.getMemberId().equals(memberId)) {
            throw new ForbiddenException(ChatErrorMessage.SESSION_FORBIDDEN.message());
        }
    }
}
