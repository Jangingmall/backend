package com.jangingmall.backend.chatbot.application;

import com.jangingmall.backend.chatbot.domain.AiChatClient;
import com.jangingmall.backend.chatbot.domain.AiChatClient.AiChatResult;
import com.jangingmall.backend.chatbot.domain.AiChatClient.ProductCard;
import com.jangingmall.backend.member.infrastructure.EmailSenderService;
import com.jangingmall.backend.support.PostgresIntegrationBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * open-in-view=false 환경에서 sendMessage 결과를 트랜잭션 밖에서 JSON으로 직렬화해도
 * 지연 로딩 컬렉션(purposeTags) 때문에 실패하지 않는지 실제 Postgres로 검증한다.
 */
@ActiveProfiles("test")
@SpringBootTest(properties = "management.server.port=-1")
class ChatServiceLazyCollectionIntegrationTest extends PostgresIntegrationBase {

    private static final Long MEMBER_ID = 1L;

    @MockitoBean
    private EmailSenderService emailSenderService;
    @MockitoBean
    private AiChatClient aiChatClient;

    @Autowired
    private ChatService chatService;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("추천 상품 카드의 purposeTags는 트랜잭션이 끝난 뒤 직렬화해도 실패하지 않는다")
    void sendMessage_resultSerializesOutsideTransaction() {
        Long productId = jdbcTemplate.queryForObject("SELECT MIN(product_id) FROM product", Long.class);
        jdbcTemplate.update("INSERT INTO product_purpose_tag (product_id, purpose_tag) VALUES (?, ?)", productId, "WEDDING");

        var session = chatService.createSession(new ChatCommand.CreateSession(MEMBER_ID));
        when(aiChatClient.chat(any(), any(), any())).thenReturn(
            new AiChatResult("추천드려요", "gift_recommendation", List.of(new ProductCard(productId, "이유")), List.of()));

        ChatResponse.SendResult result = chatService.sendMessage(
            new ChatCommand.SendMessage(session.sessionId(), MEMBER_ID, "선물 추천해줘"));

        String json = objectMapper.writeValueAsString(result);
        assertThat(json).contains("\"purposeTags\"").contains("WEDDING");
    }
}
