package com.jangingmall.backend.chatbot.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jangingmall.backend.chatbot.domain.AiChatClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class RestAiChatClientTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private MockRestServiceServer mockServer;
    private RestAiChatClient chatClient;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        mockServer = MockRestServiceServer.bindTo(restTemplate).build();
        RestClient restClient = RestClient.builder(restTemplate).baseUrl("http://ai-server").build();
        chatClient = new RestAiChatClient(restClient);
    }

    @Test
    @DisplayName("AI 응답 성공 시 products 배열을 ProductCard 목록으로 반환한다")
    void chat_success() throws Exception {
        String responseBody = OBJECT_MAPPER.writeValueAsString(Map.of(
            "reply", "추천 상품입니다",
            "intent", "gift_recommendation",
            "product_ids", List.of(1),
            "suggestions", List.of("다른 추천")
        ));
        mockServer.expect(requestTo("http://ai-server/ai/chat"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        AiChatClient.AiChatResult result = chatClient.chat(UUID.randomUUID(), "선물 추천", List.of());

        assertThat(result.reply()).isEqualTo("추천 상품입니다");
        assertThat(result.products()).hasSize(1);
        assertThat(result.products().get(0).productId()).isEqualTo(1L);
        assertThat(result.suggestions()).containsExactly("다른 추천");
    }

    @Test
    @DisplayName("AI products가 빈 배열이면 빈 ProductCard 목록을 반환한다")
    void chat_emptyProducts() throws Exception {
        String responseBody = OBJECT_MAPPER.writeValueAsString(Map.of(
            "reply", "추천 결과가 없습니다",
            "intent", "unknown",
            "products", List.of(),
            "suggestions", List.of()
        ));
        mockServer.expect(requestTo("http://ai-server/ai/chat"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        AiChatClient.AiChatResult result = chatClient.chat(UUID.randomUUID(), "질문", List.of());

        assertThat(result.products()).isEmpty();
        assertThat(result.reply()).isEqualTo("추천 결과가 없습니다");
    }

    @Test
    @DisplayName("AI 서버 오류 시 최대 2회 재시도 후 fallback 응답을 반환한다")
    void chat_retryAndFallback() {
        mockServer.expect(requestTo("http://ai-server/ai/chat")).andRespond(withServerError());
        mockServer.expect(requestTo("http://ai-server/ai/chat")).andRespond(withServerError());
        mockServer.expect(requestTo("http://ai-server/ai/chat")).andRespond(withServerError());

        AiChatClient.AiChatResult result = chatClient.chat(UUID.randomUUID(), "질문", List.of());

        mockServer.verify();
        assertThat(result.reply()).isEqualTo("현재 AI 추천을 이용할 수 없습니다");
        assertThat(result.products()).isEmpty();
        assertThat(result.suggestions()).isEmpty();
    }

    @Test
    @DisplayName("첫 번째 시도 실패 후 두 번째에 성공하면 정상 응답을 반환한다")
    void chat_successOnSecondAttempt() throws Exception {
        String responseBody = OBJECT_MAPPER.writeValueAsString(Map.of(
            "reply", "재시도 성공",
            "intent", "gift",
            "products", List.of(),
            "suggestions", List.of()
        ));
        mockServer.expect(requestTo("http://ai-server/ai/chat")).andRespond(withServerError());
        mockServer.expect(requestTo("http://ai-server/ai/chat"))
            .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        AiChatClient.AiChatResult result = chatClient.chat(UUID.randomUUID(), "질문", List.of());

        mockServer.verify();
        assertThat(result.reply()).isEqualTo("재시도 성공");
    }
}
