package com.jangingmall.backend.content.infrastructure;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import com.jangingmall.backend.content.domain.AiImageFetchException;
import com.jangingmall.backend.content.domain.AiJobAccepted;
import com.jangingmall.backend.content.domain.AiProductSyncPayload;
import com.jangingmall.backend.content.domain.AiProductUpdatePayload;
import com.jangingmall.backend.content.domain.AiProductSyncPayload.ArtisanInfo;
import com.jangingmall.backend.content.domain.AiProductSyncPayload.ProductInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class RestAiContentClientTest {

    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder()
        .findAndAddModules()
        .build();

    private static final byte[] PNG_BYTES = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 0};
    private static final byte[] JPEG_BYTES = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0};
    private static final byte[] WEBP_BYTES = {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'};
    private static final byte[] GIF_BYTES = {'G', 'I', 'F', '8', '9', 'a', 0, 0};

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
        contentClient = new RestAiContentClient(generationClient, syncClient, AI_INTERNAL_TOKEN, OBJECT_MAPPER, httpClient, new SimpleMeterRegistry());
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
    @DisplayName("이미지 URL이 http(s)가 아니면 AI 서버에 요청하지 않고 AiImageFetchException을 던진다")
    void submitJob_invalidImageUrl_failsWithoutCallingAi() {
        assertThatThrownBy(() -> contentClient.submitJob(1L, 10L, List.of("1"), "상품명", "과정", "관리법"))
            .isInstanceOf(AiImageFetchException.class);
        assertThatThrownBy(() -> contentClient.submitJob(1L, 10L,
            List.of("https://<이미지-버킷-또는-CDN>/images/product/63/<imageId>/1280w.webp"), "상품명", "과정", "관리법"))
            .isInstanceOf(AiImageFetchException.class);

        generationMockServer.verify();
    }

    @Test
    @DisplayName("이미지 다운로드가 404이거나 이미지가 아닌 응답이면 AI 서버에 요청하지 않는다")
    void submitJob_badImageResponse_failsWithoutCallingAi() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/missing", exchange -> respond(exchange, 404, "text/html", "not found"));
        server.createContext("/html", exchange -> respond(exchange, 200, "text/html", "<html>error</html>"));
        server.start();
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            assertThatThrownBy(() -> contentClient.submitJob(1L, 10L, List.of(base + "/missing"), "상품명", "과정", "관리법"))
                .isInstanceOf(AiImageFetchException.class);
            assertThatThrownBy(() -> contentClient.submitJob(1L, 10L, List.of(base + "/html"), "상품명", "과정", "관리법"))
                .isInstanceOf(AiImageFetchException.class);
        } finally {
            server.stop(0);
        }

        generationMockServer.verify();
    }

    @Test
    @DisplayName("이미지 다운로드가 성공하면 AI 서버에 job을 제출한다")
    void submitJob_validImage_submits() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/ok", exchange -> respond(exchange, 200, "image/webp", WEBP_BYTES));
        server.start();
        try {
            String acceptedJson = OBJECT_MAPPER.writeValueAsString(Map.of(
                "product_id", "10", "job_id", "job-abc", "request_id", "req-def", "status", "QUEUED",
                "status_url", "http://ai/status/job-abc", "created_at", OffsetDateTime.now().toString()));
            generationMockServer
                .expect(requestTo("http://ai-content-server/internal/v1/ai/detail-page-jobs"))
                .andRespond(withSuccess(acceptedJson, MediaType.APPLICATION_JSON));

            AiJobAccepted result = contentClient.submitJob(1L, 10L,
                List.of("http://127.0.0.1:" + server.getAddress().getPort() + "/ok"), "상품명", "과정", "관리법");

            assertThat(result.jobId()).isEqualTo("job-abc");
        } finally {
            server.stop(0);
        }

        generationMockServer.verify();
    }

    @Test
    @DisplayName("실제 이미지 종류(PNG, JPEG, WebP)에 맞는 파일명과 Content-Type으로 AI 서버에 전송한다")
    void submitJob_declaresActualImageMimeType() throws Exception {
        record Case(String path, byte[] bytes, String mime, String extension) {}
        List<Case> cases = List.of(
            new Case("/png", PNG_BYTES, "image/png", "png"),
            new Case("/jpg", JPEG_BYTES, "image/jpeg", "jpg"),
            new Case("/webp", WEBP_BYTES, "image/webp", "webp"));
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        for (Case c : cases) {
            // 서버가 잘못된 Content-Type(octet-stream)을 줘도 바이트 시그니처로 판별해야 한다
            server.createContext(c.path(), exchange -> respond(exchange, 200, "application/octet-stream", c.bytes()));
        }
        server.start();
        try {
            String acceptedJson = OBJECT_MAPPER.writeValueAsString(Map.of(
                "product_id", "10", "job_id", "job-abc", "request_id", "req-def", "status", "QUEUED",
                "status_url", "http://ai/status/job-abc", "created_at", OffsetDateTime.now().toString()));
            for (Case c : cases) {
                generationMockServer.reset();
                generationMockServer
                    .expect(requestTo("http://ai-content-server/internal/v1/ai/detail-page-jobs"))
                    .andExpect(content().string(containsString("filename=\"product_image." + c.extension() + "\"")))
                    .andExpect(content().string(containsString("Content-Type: " + c.mime())))
                    .andRespond(withSuccess(acceptedJson, MediaType.APPLICATION_JSON));

                contentClient.submitJob(1L, 10L,
                    List.of("http://127.0.0.1:" + server.getAddress().getPort() + c.path()), "상품명", "과정", "관리법");

                generationMockServer.verify();
            }
        } finally {
            server.stop(0);
        }
    }

    private static final String ACCEPTED_JSON_FOR_PHOTOS =
        "{\"product_id\":\"10\",\"job_id\":\"job-abc\",\"request_id\":\"req-def\",\"status\":\"QUEUED\","
            + "\"status_url\":\"http://ai/status/job-abc\",\"created_at\":\"2026-10-01T00:00:00Z\"}";

    private static int countOccurrences(String text, String needle) {
        int count = 0;
        for (int from = text.indexOf(needle); from >= 0; from = text.indexOf(needle, from + needle.length())) {
            count++;
        }
        return count;
    }

    @Test
    @DisplayName("사진이 여러 장이면 첫 장은 product_image, 나머지는 product_images 파트로 반복해 보낸다")
    void submitJob_sendsAdditionalPhotosAsProductImages() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/a", exchange -> respond(exchange, 200, "image/webp", WEBP_BYTES));
        server.createContext("/b", exchange -> respond(exchange, 200, "image/png", PNG_BYTES));
        server.createContext("/c", exchange -> respond(exchange, 200, "image/jpeg", JPEG_BYTES));
        server.start();
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            generationMockServer
                .expect(requestTo("http://ai-content-server/internal/v1/ai/detail-page-jobs"))
                .andExpect(request -> {
                    String body = ((org.springframework.mock.http.client.MockClientHttpRequest) request).getBodyAsString();
                    assertThat(countOccurrences(body, "name=\"product_image\"")).isEqualTo(1);
                    assertThat(countOccurrences(body, "name=\"product_images\"")).isEqualTo(2);
                    assertThat(body).contains("filename=\"product_image.webp\"")
                        .contains("filename=\"product_image_2.png\"")
                        .contains("filename=\"product_image_3.jpg\"");
                })
                .andRespond(withSuccess(ACCEPTED_JSON_FOR_PHOTOS, MediaType.APPLICATION_JSON));

            contentClient.submitJob(1L, 10L, List.of(base + "/a", base + "/b", base + "/c"), "상품명", "과정", "관리법");
        } finally {
            server.stop(0);
        }

        generationMockServer.verify();
    }

    @Test
    @DisplayName("추가 사진을 내려받지 못하면 그 사진만 빼고 제출한다")
    void submitJob_skipsFailedAdditionalPhoto() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/a", exchange -> respond(exchange, 200, "image/webp", WEBP_BYTES));
        server.createContext("/missing", exchange -> respond(exchange, 404, "text/html", "not found"));
        server.createContext("/c", exchange -> respond(exchange, 200, "image/jpeg", JPEG_BYTES));
        server.start();
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            generationMockServer
                .expect(requestTo("http://ai-content-server/internal/v1/ai/detail-page-jobs"))
                .andExpect(request -> {
                    String body = ((org.springframework.mock.http.client.MockClientHttpRequest) request).getBodyAsString();
                    assertThat(countOccurrences(body, "name=\"product_images\"")).isEqualTo(1);
                })
                .andRespond(withSuccess(ACCEPTED_JSON_FOR_PHOTOS, MediaType.APPLICATION_JSON));

            AiJobAccepted result = contentClient.submitJob(1L, 10L,
                List.of(base + "/a", base + "/missing", base + "/c"), "상품명", "과정", "관리법");

            assertThat(result.jobId()).isEqualTo("job-abc");
        } finally {
            server.stop(0);
        }

        generationMockServer.verify();
    }

    @Test
    @DisplayName("대표 사진 포함 12장을 넘는 사진은 보내지 않는다")
    void submitJob_limitsToTwelvePhotos() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/p", exchange -> respond(exchange, 200, "image/webp", WEBP_BYTES));
        server.start();
        try {
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/p";
            generationMockServer
                .expect(requestTo("http://ai-content-server/internal/v1/ai/detail-page-jobs"))
                .andExpect(request -> {
                    String body = ((org.springframework.mock.http.client.MockClientHttpRequest) request).getBodyAsString();
                    assertThat(countOccurrences(body, "name=\"product_images\"")).isEqualTo(11);
                })
                .andRespond(withSuccess(ACCEPTED_JSON_FOR_PHOTOS, MediaType.APPLICATION_JSON));

            contentClient.submitJob(1L, 10L, java.util.Collections.nCopies(15, url), "상품명", "과정", "관리법");
        } finally {
            server.stop(0);
        }

        generationMockServer.verify();
    }

    @Test
    @DisplayName("PNG·JPEG·WebP가 아닌 이미지(GIF)나 시그니처가 맞지 않는 본문은 AI 서버에 요청하지 않는다")
    void submitJob_unsupportedImageBytes_failsWithoutCallingAi() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/gif", exchange -> respond(exchange, 200, "image/gif", GIF_BYTES));
        server.createContext("/fake", exchange -> respond(exchange, 200, "image/png", "<html>not an image</html>"));
        server.start();
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            assertThatThrownBy(() -> contentClient.submitJob(1L, 10L, List.of(base + "/gif"), "상품명", "과정", "관리법"))
                .isInstanceOf(AiImageFetchException.class)
                .hasMessageContaining("지원하지 않는 이미지 형식");
            assertThatThrownBy(() -> contentClient.submitJob(1L, 10L, List.of(base + "/fake"), "상품명", "과정", "관리법"))
                .isInstanceOf(AiImageFetchException.class);
        } finally {
            server.stop(0);
        }

        generationMockServer.verify();
    }

    private static void respond(HttpExchange exchange, int status, String contentType, String body) throws IOException {
        respond(exchange, status, contentType, body.getBytes(StandardCharsets.UTF_8));
    }

    private static void respond(HttpExchange exchange, int status, String contentType, byte[] bytes) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", contentType);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    @Test
    @DisplayName("상품 동기화 — syncProduct가 챗봇 서버 /ai/products/sync로 전송된다")
    void syncProduct_routesToChatBotServer() throws Exception {
        syncMockServer.expect(requestTo("http://ai-chat-server/ai/products"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withNoContent());

        ArtisanInfo artisanInfo = new ArtisanInfo(1L, "김장인공방", "ARTISAN", "경북 문경");
        ProductInfo productInfo = new ProductInfo(
            42L, "청자 다완", "KITCHEN", "TEAWARE", "청자", 85000, List.of(), List.of(), "손으로 빚음", "물 닦기", "청색", "ON_SALE"
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
            "청자 다완 (수정)", "KITCHEN", "TEAWARE", "청자", 90000, List.of(), List.of(), "손으로 빚음", "물 닦기", "청색", "ON_SALE"
        );
        contentClient.updateProduct(42L, new AiProductUpdatePayload(patch));

        syncMockServer.verify();
    }

    @Test
    @DisplayName("상품 상태 동기화 — updateProductStatus는 상태 한 필드만 PUT으로 보내 챗봇이 재임베딩하지 않게 한다")
    void updateProductStatus_sendsOnlyStatus() {
        syncMockServer.expect(requestTo("http://ai-chat-server/ai/products/42"))
            .andExpect(method(HttpMethod.PUT))
            .andExpect(content().string(containsString("\"status\":\"SOLD_OUT\"")))
            .andExpect(content().string(org.hamcrest.Matchers.not(containsString("\"name\""))))
            .andExpect(content().string(org.hamcrest.Matchers.not(containsString("making_story"))))
            .andRespond(withNoContent());

        contentClient.updateProductStatus(42L, "SOLD_OUT");

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

    // ── 렌더링 요청(approveRender) ────────────────────────────────────────────

    private static final String STATUS_WITH_DRAFT = """
        {"job_id":"job-1","request_id":"req-1","status":"DRAFT_READY","progress":100,
         "draft":{"draft_id":"job-1","generation_id":"42","version":1,
                  "draft":{"product_name":"청자 다완","summary":"요약","hero_headline":"제목","hero_description":"설명"}}}
        """;

    private void expectDraftStatus(String body) {
        generationMockServer
            .expect(requestTo("http://ai-content-server/internal/v1/ai/detail-page-jobs/job-1"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("X-AI-Internal-Token", AI_INTERNAL_TOKEN))
            .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }

    @Test
    @DisplayName("렌더링 요청 — 상태 조회로 받은 초안을 그대로 담아 multipart metadata로 detail-page-renders에 전송한다")
    void approveRender_sendsDraftAsMultipartMetadata() {
        expectDraftStatus(STATUS_WITH_DRAFT);
        generationMockServer
            .expect(requestTo("http://ai-content-server/internal/v1/ai/detail-page-renders"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("X-AI-Internal-Token", AI_INTERNAL_TOKEN))
            .andExpect(header("Idempotency-Key", "render-42"))
            .andExpect(header("Content-Type", containsString("multipart/form-data")))
            .andExpect(content().string(containsString("name=\"metadata\"")))
            .andExpect(content().string(containsString("\"product_id\":\"10\"")))
            .andExpect(content().string(containsString("\"idempotency_key\":\"render-42\"")))
            .andExpect(content().string(containsString("\"draft_id\":\"job-1\"")))
            .andExpect(content().string(containsString("\"hero_headline\":\"제목\"")))
            .andExpect(content().string(containsString("\"source_generation_id\":\"42\"")))
            .andRespond(withSuccess("{\"status\":\"COMPLETED\"}", MediaType.APPLICATION_JSON));

        contentClient.approveRender("job-1", 42L, 10L);

        generationMockServer.verify();
    }

    @Test
    @DisplayName("렌더링 요청 — 상태 응답에 초안이 없으면 렌더링을 요청하지 않고 예외를 던진다")
    void approveRender_withoutDraft_failsWithoutRenderCall() {
        expectDraftStatus("{\"job_id\":\"job-1\",\"status\":\"QUEUED\",\"draft\":null}");

        assertThatThrownBy(() -> contentClient.approveRender("job-1", 42L, 10L))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("초안");

        generationMockServer.verify();
    }

    @Test
    @DisplayName("렌더링 요청 — 이미 같은 키로 렌더 중(409 in progress)이면 중복 요청으로 보고 정상 종료한다")
    void approveRender_alreadyInProgress_isIgnored() {
        expectDraftStatus(STATUS_WITH_DRAFT);
        generationMockServer
            .expect(requestTo("http://ai-content-server/internal/v1/ai/detail-page-renders"))
            .andRespond(withStatus(HttpStatus.CONFLICT)
                .body("{\"detail\":\"Approval is already in progress\"}")
                .contentType(MediaType.APPLICATION_JSON));

        contentClient.approveRender("job-1", 42L, 10L);

        generationMockServer.verify();
    }

    @Test
    @DisplayName("렌더링 요청 — 멱등성 키 충돌(409)은 진행 중이 아니므로 예외를 전파한다")
    void approveRender_idempotencyConflict_throws() {
        expectDraftStatus(STATUS_WITH_DRAFT);
        generationMockServer
            .expect(requestTo("http://ai-content-server/internal/v1/ai/detail-page-renders"))
            .andRespond(withStatus(HttpStatus.CONFLICT)
                .body("{\"detail\":\"Idempotency key conflict\"}")
                .contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> contentClient.approveRender("job-1", 42L, 10L))
            .isInstanceOf(HttpClientErrorException.Conflict.class);
    }

    @Test
    @DisplayName("렌더링 요청 — AI가 422 등으로 거절하면 예외를 전파한다")
    void approveRender_rejected_throws() {
        expectDraftStatus(STATUS_WITH_DRAFT);
        generationMockServer
            .expect(requestTo("http://ai-content-server/internal/v1/ai/detail-page-renders"))
            .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY)
                .body("{\"detail\":\"invalid\"}")
                .contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> contentClient.approveRender("job-1", 42L, 10L))
            .isInstanceOf(HttpClientErrorException.class);
    }
}
