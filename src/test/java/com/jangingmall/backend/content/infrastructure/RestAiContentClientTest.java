package com.jangingmall.backend.content.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class RestAiContentClientTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private MockRestServiceServer mockServer;
    private RestAiContentClient contentClient;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        mockServer = MockRestServiceServer.bindTo(restTemplate).build();
        RestClient restClient = RestClient.builder(restTemplate).baseUrl("http://ai-server").build();
        contentClient = new RestAiContentClient(restClient);
    }

    @Test
    @DisplayName("AI 콘텐츠 생성 요청 — generationId, productId, images, productName, howMade, careTips가 올바르게 직렬화되어 /ai/products로 전송된다")
    void requestGeneration_serializesPayloadCorrectly() throws Exception {
        String blocksJson = OBJECT_MAPPER.writeValueAsString(
            List.of(Map.of("order", 1, "tag", "h2", "text", "청자 다완의 이야기"))
        );
        mockServer.expect(requestTo("http://ai-server/ai/products"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(jsonPath("$.generationId").value(42))
            .andExpect(jsonPath("$.productId").value(10))
            .andExpect(jsonPath("$.productName").value("청자 다완"))
            .andExpect(jsonPath("$.howMade").value("손으로 직접 빚음"))
            .andExpect(jsonPath("$.careTips").value("물기 닦아서 보관"))
            .andExpect(jsonPath("$.images[0]").value("imageId1"))
            .andExpect(jsonPath("$.images[1]").value("imageId2"))
            .andRespond(withSuccess(blocksJson, MediaType.APPLICATION_JSON));

        String result = contentClient.requestGeneration(
            42L, 10L, List.of("imageId1", "imageId2"), "청자 다완", "손으로 직접 빚음", "물기 닦아서 보관"
        );

        mockServer.verify();
        JsonNode parsed = OBJECT_MAPPER.readTree(result);
        assertThat(parsed.isArray()).isTrue();
        assertThat(parsed.get(0).path("tag").asText()).isEqualTo("h2");
    }

    @Test
    @DisplayName("AI 콘텐츠 생성 요청 — images 배열이 비어 있어도 빈 배열로 전송된다")
    void requestGeneration_emptyImages() throws Exception {
        String blocksJson = OBJECT_MAPPER.writeValueAsString(List.of());
        mockServer.expect(requestTo("http://ai-server/ai/products"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(jsonPath("$.images").isArray())
            .andRespond(withSuccess(blocksJson, MediaType.APPLICATION_JSON));

        contentClient.requestGeneration(1L, 10L, List.of(), "상품명", "과정", "관리법");

        mockServer.verify();
    }

    @Test
    @DisplayName("AI 콘텐츠 생성 요청 — AI 서버 오류 시 RestClientException이 전파된다")
    void requestGeneration_serverError_throwsException() {
        mockServer.expect(requestTo("http://ai-server/ai/products"))
            .andRespond(withServerError());

        org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> contentClient.requestGeneration(1L, 10L, List.of("img1"), "상품명", "과정", "관리법")
        ).isInstanceOf(org.springframework.web.client.RestClientException.class);

        mockServer.verify();
    }
}
