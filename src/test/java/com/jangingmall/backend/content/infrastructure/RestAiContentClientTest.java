package com.jangingmall.backend.content.infrastructure;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import com.jangingmall.backend.content.domain.AiJobAccepted;
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

import java.net.http.HttpClient;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class RestAiContentClientTest {

    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder()
        .findAndAddModules()
        .build();

    private MockRestServiceServer generationMockServer;
    private MockRestServiceServer syncMockServer;
    private RestAiContentClient contentClient;

    private static final String AI_INTERNAL_TOKEN = "test-internal-token";

    @BeforeEach
    void setUp() {
        RestTemplate generationTemplate = new RestTemplate();
        RestTemplate syncTemplate = new RestTemplate();
        generationMockServer = MockRestServiceServer.bindTo(generationTemplate).build();
        syncMockServer = MockRestServiceServer.bindTo(syncTemplate).build();
        RestClient generationClient = RestClient.builder(generationTemplate).baseUrl("http://ai-content-server").build();
        RestClient syncClient = RestClient.builder(syncTemplate).baseUrl("http://ai-chat-server").build();
        HttpClient httpClient = HttpClient.newBuilder().build();
        contentClient = new RestAiContentClient(generationClient, syncClient, AI_INTERNAL_TOKEN, OBJECT_MAPPER, httpClient);
    }

    @Test
    @DisplayName("AI job 제출 — multipart로 /internal/v1/ai/detail-page-jobs에 전송하고 jobId를 반환한다")
    void submitJob_sendsMultipartAndReturnsJobId() throws Exception {
        String acceptedJson = OBJECT_MAPPER.writeValueAsString(Map.of(
            "product_id", "10",
            "job_id", "job-abc",
            "request_id", "req-def",
            "status", "QUEUED",
            "status_url", "http://ai/status/job-abc",
            "created_at", OffsetDateTime.now().toString()
        ));
        generationMockServer
            .expect(requestTo("http://ai-content-server/internal/v1/ai/detail-page-jobs"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("X-AI-Internal-Token", AI_INTERNAL_TOKEN))
            .andExpect(header("Idempotency-Key", "42"))
            .andRespond(withSuccess(acceptedJson, MediaType.APPLICATION_JSON));

        AiJobAccepted result = contentClient.submitJob(42L, 10L, List.of(), "청자 다완", "손으로 빚음", "물 닦기");

        generationMockServer.verify();
        assertThat(result.jobId()).isEqualTo("job-abc");
        assertThat(result.requestId()).isEqualTo("req-def");
        assertThat(result.statusUrl()).isEqualTo("http://ai/status/job-abc");
    }

    @Test
    @DisplayName("AI job 제출 — AI 서버 오류 시 RestClientException이 전파된다")
    void submitJob_serverError_throwsException() {
        generationMockServer
            .expect(requestTo("http://ai-content-server/internal/v1/ai/detail-page-jobs"))
            .andRespond(withServerError());

        assertThatThrownBy(
            () -> contentClient.submitJob(1L, 10L, List.of(), "상품명", "과정", "관리법")
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
        contentClient.updateProduct(42L, new AiProductUpdatePayload(patch));

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
