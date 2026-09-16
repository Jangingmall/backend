package com.jangingmall.backend.chatbot.application;

import com.jangingmall.backend.chatbot.domain.AiChatClient;
import com.jangingmall.backend.chatbot.domain.AiChatClient.AiChatResult;
import com.jangingmall.backend.chatbot.domain.AiChatClient.ProductCard;
import com.jangingmall.backend.chatbot.domain.ChatMessage;
import com.jangingmall.backend.chatbot.domain.ChatMessageRepository;
import com.jangingmall.backend.chatbot.domain.ChatSender;
import com.jangingmall.backend.chatbot.domain.ChatSession;
import com.jangingmall.backend.chatbot.domain.ChatSessionRepository;
import com.jangingmall.backend.global.exception.BusinessRuleViolationException;
import com.jangingmall.backend.global.exception.ForbiddenException;
import com.jangingmall.backend.global.exception.NotFoundException;
import com.jangingmall.backend.product.domain.Category;
import com.jangingmall.backend.product.domain.Product;
import com.jangingmall.backend.product.domain.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock
    private ChatSessionRepository sessionRepository;
    @Mock
    private ChatMessageRepository messageRepository;
    @Mock
    private AiChatClient aiChatClient;
    @Mock
    private ProductRepository productRepository;

    private ChatService chatService;

    @BeforeEach
    void setUp() {
        chatService = new ChatService(sessionRepository, messageRepository, aiChatClient, productRepository);
    }

    @Test
    @DisplayName("세션 생성 시 저장 후 반환된다")
    void createSession_success() {
        ChatSession session = ChatSession.create(1L);
        ReflectionTestUtils.setField(session, "sessionId", UUID.randomUUID());
        when(sessionRepository.save(any())).thenReturn(session);

        ChatResponse.SessionView result = chatService.createSession(new ChatCommand.CreateSession(1L));

        assertThat(result.memberId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("메시지 전송 시 AI 응답 상품카드가 조립되어 반환된다")
    void sendMessage_success() {
        UUID sessionId = UUID.randomUUID();
        ChatSession session = ChatSession.create(1L);
        ReflectionTestUtils.setField(session, "sessionId", sessionId);

        ChatMessage userMsg = ChatMessage.of(sessionId, ChatSender.USER, "엄마 선물 추천해줘");
        ReflectionTestUtils.setField(userMsg, "messageId", 1L);
        ChatMessage botMsg = ChatMessage.of(sessionId, ChatSender.ADMIN, "다음 상품을 추천드려요");
        ReflectionTestUtils.setField(botMsg, "messageId", 2L);

        Product product = Product.create(1L, null, null, "청자 다완", "설명", 85000, 10, null);
        ReflectionTestUtils.setField(product, "id", 1L);

        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(messageRepository.save(any())).thenReturn(userMsg).thenReturn(botMsg);
        when(messageRepository.findBySessionId(sessionId)).thenReturn(List.of());
        when(aiChatClient.chat(any(), any(), any())).thenReturn(
            new AiChatResult("다음 상품을 추천드려요", "gift_recommendation",
                List.of(new ProductCard(1L, "경력 60년의 장인이 직접 제작")),
                List.of("다른 종류로"))
        );
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        ChatResponse.SendResult result = chatService.sendMessage(
            new ChatCommand.SendMessage(sessionId, 1L, "엄마 선물 추천해줘")
        );

        assertThat(result.recommendedProducts()).hasSize(1);
        assertThat(result.recommendedProducts().get(0).productId()).isEqualTo(1L);
        assertThat(result.recommendedProducts().get(0).reason()).isEqualTo("경력 60년의 장인이 직접 제작");
        assertThat(result.suggestions()).containsExactly("다른 종류로");
    }

    @Test
    @DisplayName("AI 응답에 없는 상품 ID는 카드에서 제외된다")
    void sendMessage_skipsUnknownProduct() {
        UUID sessionId = UUID.randomUUID();
        ChatSession session = ChatSession.create(1L);
        ReflectionTestUtils.setField(session, "sessionId", sessionId);

        ChatMessage userMsg = ChatMessage.of(sessionId, ChatSender.USER, "질문");
        ReflectionTestUtils.setField(userMsg, "messageId", 1L);
        ChatMessage botMsg = ChatMessage.of(sessionId, ChatSender.ADMIN, "응답");
        ReflectionTestUtils.setField(botMsg, "messageId", 2L);

        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(messageRepository.save(any())).thenReturn(userMsg).thenReturn(botMsg);
        when(messageRepository.findBySessionId(sessionId)).thenReturn(List.of());
        when(aiChatClient.chat(any(), any(), any())).thenReturn(
            new AiChatResult("응답", "intent",
                List.of(new ProductCard(999L, "이유")), List.of())
        );
        when(productRepository.findById(999L)).thenReturn(Optional.empty());

        ChatResponse.SendResult result = chatService.sendMessage(
            new ChatCommand.SendMessage(sessionId, 1L, "질문")
        );

        assertThat(result.recommendedProducts()).isEmpty();
    }

    @Test
    @DisplayName("존재하지 않는 세션에 메시지 전송 시 NotFoundException이 발생한다")
    void sendMessage_sessionNotFound() {
        when(sessionRepository.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> chatService.sendMessage(
            new ChatCommand.SendMessage(UUID.randomUUID(), 1L, "질문")
        )).isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("타 회원 세션에 접근하면 ForbiddenException이 발생한다")
    void sendMessage_forbidden() {
        UUID sessionId = UUID.randomUUID();
        ChatSession session = ChatSession.create(1L);
        ReflectionTestUtils.setField(session, "sessionId", sessionId);
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> chatService.sendMessage(
            new ChatCommand.SendMessage(sessionId, 99L, "질문")
        )).isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("종료된 세션에 메시지 전송 시 BusinessRuleViolationException이 발생한다")
    void sendMessage_sessionEnded() {
        UUID sessionId = UUID.randomUUID();
        ChatSession session = ChatSession.create(1L);
        ReflectionTestUtils.setField(session, "sessionId", sessionId);
        session.end();
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> chatService.sendMessage(
            new ChatCommand.SendMessage(sessionId, 1L, "질문")
        )).isInstanceOf(BusinessRuleViolationException.class)
            .hasMessageContaining("종료된 세션");
    }

    @Test
    @DisplayName("세션 종료 시 정상 처리된다")
    void endSession_success() {
        UUID sessionId = UUID.randomUUID();
        ChatSession session = ChatSession.create(1L);
        ReflectionTestUtils.setField(session, "sessionId", sessionId);
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));

        chatService.endSession(new ChatCommand.EndSession(sessionId, 1L));

        assertThat(session.isEnded()).isTrue();
    }
}
