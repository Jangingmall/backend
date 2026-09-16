package com.jangingmall.backend.chatbot.presentation;

import com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import com.jangingmall.backend.chatbot.application.ChatResponse;
import com.jangingmall.backend.chatbot.application.ChatService;
import com.jangingmall.backend.chatbot.domain.ChatSender;
import com.jangingmall.backend.global.config.SecurityConfig;
import com.jangingmall.backend.global.docs.RestDocsControllerTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.delete;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ChatController.class)
@Import(SecurityConfig.class)
class ChatControllerTest extends RestDocsControllerTest {

    @MockitoBean
    private ChatService chatService;

    private static final UUID SESSION_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private static final ChatResponse.SessionView SAMPLE_SESSION = new ChatResponse.SessionView(
        SESSION_ID, 1L, LocalDateTime.of(2026, 9, 3, 10, 0), null
    );

    private static final ChatResponse.MessageView USER_MSG = new ChatResponse.MessageView(
        1L, SESSION_ID, ChatSender.USER, "엄마 환갑 선물 추천해줘", LocalDateTime.of(2026, 9, 3, 10, 1)
    );

    private static final ChatResponse.MessageView BOT_MSG = new ChatResponse.MessageView(
        2L, SESSION_ID, ChatSender.ADMIN, "고급스러운 도자기를 추천드립니다.", LocalDateTime.of(2026, 9, 3, 10, 1)
    );

    private static final ChatResponse.SendResult SAMPLE_SEND_RESULT = new ChatResponse.SendResult(
        USER_MSG, BOT_MSG,
        List.of(
            new ChatResponse.ProductCard(1L, "청자 다완", "https://example.com/img1.jpg", 85000, "60년 경력의 장인이 빚은 청자"),
            new ChatResponse.ProductCard(2L, "백자 찻잔", "https://example.com/img2.jpg", 45000, "순백의 아름다움")
        ),
        List.of("3만원 이하로", "다른 재질로")
    );

    @Test
    @DisplayName("챗봇 세션 생성 — 소비자가 새 대화 세션을 생성한다")
    @WithMockUser(roles = "USER")
    void createSession() throws Exception {
        when(chatService.createSession(any())).thenReturn(SAMPLE_SESSION);

        mockMvc.perform(post("/api/chatbot/sessions"))
            .andExpect(status().isCreated())
            .andDo(MockMvcRestDocumentationWrapper.document(
                "chatbot-session-create",
                resource(ResourceSnippetParameters.builder()
                    .tag("챗봇")
                    .summary("챗봇 세션 생성")
                    .description("소비자가 새 챗봇 대화 세션을 생성합니다.")
                    .responseFields(successEnvelopeFields(
                        fieldWithPath("data.sessionId").type(JsonFieldType.STRING).description("세션 ID (UUID)"),
                        fieldWithPath("data.memberId").type(JsonFieldType.NUMBER).description("회원 ID"),
                        fieldWithPath("data.createdAt").type(JsonFieldType.STRING).description("세션 생성일시"),
                        fieldWithPath("data.endedAt").type(JsonFieldType.VARIES).optional().description("세션 종료일시 (종료 전 null)")
                    ))
                    .build()
                )
            ));
    }

    @Test
    @DisplayName("메시지 전송 — 소비자가 메시지를 보내면 AI 추천 결과를 반환한다")
    @WithMockUser(roles = "USER")
    void sendMessage() throws Exception {
        when(chatService.sendMessage(any())).thenReturn(SAMPLE_SEND_RESULT);

        mockMvc.perform(post("/api/chatbot/sessions/{sessionId}/messages", SESSION_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new ChatRequest.SendMessage("엄마 환갑 선물 추천해줘"))))
            .andExpect(status().isCreated())
            .andDo(MockMvcRestDocumentationWrapper.document(
                "chatbot-message-send",
                resource(ResourceSnippetParameters.builder()
                    .tag("챗봇")
                    .summary("챗봇 메시지 전송")
                    .description("소비자 메시지를 전송하면 AI가 추천 상품과 답변을 반환합니다. 최근 6턴 대화 이력을 맥락으로 활용합니다.")
                    .pathParameters(parameterWithName("sessionId").description("세션 ID"))
                    .requestFields(
                        fieldWithPath("content").type(JsonFieldType.STRING).description("소비자 메시지 (최대 2000자)")
                    )
                    .responseFields(successEnvelopeFields(
                        fieldWithPath("data.userMessage.messageId").type(JsonFieldType.NUMBER).description("사용자 메시지 ID"),
                        fieldWithPath("data.userMessage.sessionId").type(JsonFieldType.STRING).description("세션 ID"),
                        fieldWithPath("data.userMessage.sender").type(JsonFieldType.STRING).description("발신자 (USER)"),
                        fieldWithPath("data.userMessage.content").type(JsonFieldType.STRING).description("메시지 내용"),
                        fieldWithPath("data.userMessage.sentAt").type(JsonFieldType.STRING).description("전송일시"),
                        fieldWithPath("data.botMessage.messageId").type(JsonFieldType.NUMBER).description("봇 메시지 ID"),
                        fieldWithPath("data.botMessage.sessionId").type(JsonFieldType.STRING).description("세션 ID"),
                        fieldWithPath("data.botMessage.sender").type(JsonFieldType.STRING).description("발신자 (ADMIN)"),
                        fieldWithPath("data.botMessage.content").type(JsonFieldType.STRING).description("AI 추천 코멘트"),
                        fieldWithPath("data.botMessage.sentAt").type(JsonFieldType.STRING).description("전송일시"),
                        fieldWithPath("data.recommendedProducts").type(JsonFieldType.ARRAY).description("추천 상품 카드 목록 (최대 3개)"),
                        fieldWithPath("data.recommendedProducts[].productId").type(JsonFieldType.NUMBER).description("상품 ID"),
                        fieldWithPath("data.recommendedProducts[].productName").type(JsonFieldType.STRING).description("상품명"),
                        fieldWithPath("data.recommendedProducts[].thumbnailUrl").type(JsonFieldType.VARIES).optional().description("썸네일 URL"),
                        fieldWithPath("data.recommendedProducts[].price").type(JsonFieldType.NUMBER).description("가격"),
                        fieldWithPath("data.recommendedProducts[].reason").type(JsonFieldType.STRING).description("추천 이유"),
                        fieldWithPath("data.suggestions").type(JsonFieldType.ARRAY).description("후속 제안 칩 (최대 3개)")
                    ))
                    .build()
                )
            ));
    }

    @Test
    @DisplayName("대화 이력 조회 — 세션의 전체 메시지 목록을 시간순으로 조회한다")
    @WithMockUser(roles = "USER")
    void messages() throws Exception {
        when(chatService.findMessages(any(), any())).thenReturn(List.of(USER_MSG, BOT_MSG));

        mockMvc.perform(get("/api/chatbot/sessions/{sessionId}/messages", SESSION_ID))
            .andExpect(status().isOk())
            .andDo(MockMvcRestDocumentationWrapper.document(
                "chatbot-message-list",
                resource(ResourceSnippetParameters.builder()
                    .tag("챗봇")
                    .summary("대화 이력 조회")
                    .description("세션의 전체 메시지 목록을 전송 시간순으로 조회합니다.")
                    .pathParameters(parameterWithName("sessionId").description("세션 ID"))
                    .responseFields(successEnvelopeFields(
                        fieldWithPath("data[].messageId").type(JsonFieldType.NUMBER).description("메시지 ID"),
                        fieldWithPath("data[].sessionId").type(JsonFieldType.STRING).description("세션 ID"),
                        fieldWithPath("data[].sender").type(JsonFieldType.STRING).description("발신자 (USER/ADMIN)"),
                        fieldWithPath("data[].content").type(JsonFieldType.STRING).description("메시지 내용"),
                        fieldWithPath("data[].sentAt").type(JsonFieldType.STRING).description("전송일시")
                    ))
                    .build()
                )
            ));
    }

    @Test
    @DisplayName("세션 종료 — 소비자가 대화 세션을 종료한다")
    @WithMockUser(roles = "USER")
    void endSession() throws Exception {
        doNothing().when(chatService).endSession(any());

        mockMvc.perform(delete("/api/chatbot/sessions/{sessionId}", SESSION_ID))
            .andExpect(status().isOk())
            .andDo(MockMvcRestDocumentationWrapper.document(
                "chatbot-session-end",
                resource(ResourceSnippetParameters.builder()
                    .tag("챗봇")
                    .summary("챗봇 세션 종료")
                    .description("소비자가 대화 세션을 종료합니다. 종료된 세션에는 메시지를 전송할 수 없습니다.")
                    .pathParameters(parameterWithName("sessionId").description("세션 ID"))
                    .responseFields(successEnvelopeFields(
                        fieldWithPath("data").type(JsonFieldType.NULL).description("데이터 없음")
                    ))
                    .build()
                )
            ));
    }

    @Test
    @DisplayName("세션 생성 — 소비자 권한 없으면 403을 반환한다")
    @WithMockUser(roles = "ARTISAN")
    void createSession_forbidden() throws Exception {
        mockMvc.perform(post("/api/chatbot/sessions"))
            .andExpect(status().isForbidden());
    }
}
