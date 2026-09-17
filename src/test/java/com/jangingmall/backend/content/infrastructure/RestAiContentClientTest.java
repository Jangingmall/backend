package com.jangingmall.backend.content.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jangingmall.backend.content.domain.AiProductSyncPayload;
import com.jangingmall.backend.content.domain.AiProductUpdatePayload;
import com.jangingmall.backend.content.domain.AiProductSyncPayload.ArtisanInfo;
import com.jangingmall.backend.content.domain.AiProductSyncPayload.ProductInfo;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class RestAiContentClientTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private MockRestServiceServer generationMockServer;
    private MockRestServiceServer syncMockServer;
    private RestAiContentClient contentClient;

    @BeforeEach
    void setUp() {
        RestTemplate generationTemplate = new RestTemplate();
        RestTemplate syncTemplate = new RestTemplate();
        generationMockServer = MockRestServiceServer.bindTo(generationTemplate).build();
        syncMockServer = MockRestServiceServer.bindTo(syncTemplate).build();
        RestClient generationClient = RestClient.builder(generationTemplate).baseUrl("http://ai-content-server").build();
        RestClient syncClient = RestClient.builder(syncTemplate).baseUrl("http://ai-chat-server").build();
        contentClient = new RestAiContentClient(generationClient, syncClient);
    }

    @Test
    @DisplayName("AI 콘텐츠 생성 요청 — generationId, productId, images, productName, howMade, careTips가 올바르게 직렬화되어 /ai/products로 전송된다")
    void requestGeneration_serializesPayloadCorrectly() throws Exception {
        String blocksJson = OBJECT_MAPPER.writeValueAsString(
            List.of(Map.of("order", 1, "tag", "h2", "text", "청자 다완의 이야기"))
        );
        generationMockServer.expect(requestTo("http://ai-content-server/ai/products"))
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

        generationMockServer.verify();
        JsonNode parsed = OBJECT_MAPPER.readTree(result);
        assertThat(parsed.isArray()).isTrue();
        assertThat(parsed.get(0).path("tag").asText()).isEqualTo("h2");
    }

    @Test
    @DisplayName("AI 콘텐츠 생성 요청 — images 배열이 비어 있어도 빈 배열로 전송된다")
    void requestGeneration_emptyImages() throws Exception {
        String blocksJson = OBJECT_MAPPER.writeValueAsString(List.of());
        generationMockServer.expect(requestTo("http://ai-content-server/ai/products"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(jsonPath("$.images").isArray())
            .andRespond(withSuccess(blocksJson, MediaType.APPLICATION_JSON));

        contentClient.requestGeneration(1L, 10L, List.of(), "상품명", "과정", "관리법");

        generationMockServer.verify();
    }

    @Test
    @DisplayName("AI 콘텐츠 생성 요청 — AI 서버 오류 시 RestClientException이 전파된다")
    void requestGeneration_serverError_throwsException() {
        generationMockServer.expect(requestTo("http://ai-content-server/ai/products"))
            .andRespond(withServerError());

        assertThatThrownBy(
            () -> contentClient.requestGeneration(1L, 10L, List.of("img1"), "상품명", "과정", "관리법")
        ).isInstanceOf(org.springframework.web.client.RestClientException.class);

        generationMockServer.verify();
    }

    @Test
    @DisplayName("상품 동기화 — syncProduct가 챗봇 서버 /ai/products/sync로 전송된다")
    void syncProduct_routesToChatBotServer() throws Exception {
        syncMockServer.expect(requestTo("http://ai-chat-server/ai/products/sync"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withNoContent());

        ArtisanInfo artisanInfo = new ArtisanInfo(1L, "김장인", "ARTISAN", "소개글");
        ProductInfo productInfo = new ProductInfo(
            42L, "청자 다완", "도자기", "청자", 85000, List.of(), List.of(), "손으로 빚음", "물 닦기", 30, List.of()
        );
        contentClient.syncProduct(new AiProductSyncPayload(artisanInfo, productInfo));

        syncMockServer.verify();
    }

    @Test
    @DisplayName("상품 수정 동기화 — updateProduct가 챗봇 서버 /ai/products/{id}로 전송된다")
    void updateProduct_routesToChatBotServer() throws Exception {
        syncMockServer.expect(requestTo("http://ai-chat-server/ai/products/42"))
            .andExpect(method(HttpMethod.PUT))
            .andRespond(withNoContent());

        AiProductUpdatePayload.ProductPatch patch = new AiProductUpdatePayload.ProductPatch(
            "청자 다완 (수정)", "도자기", "청자", 90000, List.of(), List.of(), "손으로 빚음", "물 닦기", 30, List.of()
        );
        AiProductUpdatePayload payload = new AiProductUpdatePayload(patch);
        contentClient.updateProduct(42L, payload);

        syncMockServer.verify();
    }

    @Test
    @DisplayName("상품 삭제 동기화 — deleteProduct가 챗봇 서버 /ai/products/{id}로 전송된다")
    void deleteProduct_routesToChatBotServer() throws Exception {
        syncMockServer.expect(requestTo("http://ai-chat-server/ai/products/42"))
            .andExpect(method(HttpMethod.DELETE))
            .andRespond(withNoContent());

        contentClient.deleteProduct(42L);

        syncMockServer.verify();
    }
}
