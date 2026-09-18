package com.jangingmall.backend.content.infrastructure;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.jangingmall.backend.content.domain.AiContentClient;
import com.jangingmall.backend.content.domain.AiJobAccepted;
import com.jangingmall.backend.content.domain.AiProductSyncPayload;
import com.jangingmall.backend.content.domain.AiProductUpdatePayload;
import com.jangingmall.backend.global.config.AiProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

@Slf4j
@Component
class RestAiContentClient implements AiContentClient {

    private static final String DETAIL_PAGE_JOBS_PATH = "/internal/v1/ai/detail-page-jobs";
    private static final String AI_INTERNAL_TOKEN_HEADER = "X-AI-Internal-Token";
    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    private static final String DEFAULT_TEMPLATE_ID = "default-long-detail-page";
    private static final String DEFAULT_LOCALE = "ko-KR";

    private final RestClient generationClient;
    private final RestClient syncClient;
    private final String aiInternalAuthToken;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    RestAiContentClient(RestClient generationClient, RestClient syncClient,
        String aiInternalAuthToken, ObjectMapper objectMapper, HttpClient httpClient) {
        this.generationClient = generationClient;
        this.syncClient = syncClient;
        this.aiInternalAuthToken = aiInternalAuthToken;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    @Autowired
    RestAiContentClient(AiProperties aiProperties, ObjectMapper objectMapper) {
        Duration timeout = Duration.ofSeconds(aiProperties.timeoutSeconds());
        this.httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(timeout)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(timeout);
        this.generationClient = RestClient.builder()
            .baseUrl(aiProperties.contentUrl())
            .requestFactory(factory)
            .build();
        this.syncClient = RestClient.builder()
            .baseUrl(aiProperties.chatBotUrl())
            .requestFactory(factory)
            .build();
        this.aiInternalAuthToken = aiProperties.internalAuthToken();
        this.objectMapper = objectMapper;
    }

    @Override
    public AiJobAccepted submitJob(Long generationId, Long productId, List<String> images,
        String productName, String howMade, String careTips) {
        String idempotencyKey = generationId.toString();
        String metadataJson = buildMetadataJson(generationId, productId, idempotencyKey, productName, howMade, careTips);
        byte[] imageBytes = fetchFirstImage(images, generationId);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("metadata", metadataJson);
        body.add("product_image", new NamedByteArrayResource(imageBytes, "product_image.jpg"));

        log.info("AI 콘텐츠 생성 job 제출 generationId={} productId={}", generationId, productId);
        AiJobAcceptedResponse response = generationClient.post()
            .uri(DETAIL_PAGE_JOBS_PATH)
            .header(AI_INTERNAL_TOKEN_HEADER, aiInternalAuthToken)
            .header(IDEMPOTENCY_KEY_HEADER, idempotencyKey)
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(body)
            .retrieve()
            .body(AiJobAcceptedResponse.class);

        return new AiJobAccepted(response.jobId(), response.requestId(), response.statusUrl());
    }

    @Override
    public void syncProduct(AiProductSyncPayload payload) {
        try {
            syncClient.post()
                .uri("/ai/products/sync")
                .body(payload)
                .retrieve()
                .toBodilessEntity();
            log.info("AI 상품 동기화 완료 productId={}", payload.product().product_id());
        } catch (RestClientException e) {
            log.error("AI 상품 동기화 실패 productId={} reason={}", payload.product().product_id(), e.getMessage());
        }
    }

    @Override
    public void updateProduct(Long productId, AiProductUpdatePayload payload) {
        try {
            syncClient.put()
                .uri("/ai/products/{id}", productId)
                .body(payload)
                .retrieve()
                .toBodilessEntity();
            log.info("AI 상품 수정 동기화 완료 productId={}", productId);
        } catch (RestClientException e) {
            log.error("AI 상품 수정 동기화 실패 productId={} reason={}", productId, e.getMessage());
        }
    }

    @Override
    public void deleteProduct(Long productId) {
        try {
            syncClient.delete()
                .uri("/ai/products/{id}", productId)
                .retrieve()
                .toBodilessEntity();
            log.info("AI 상품 삭제 동기화 완료 productId={}", productId);
        } catch (RestClientException e) {
            log.error("AI 상품 삭제 동기화 실패 productId={} reason={}", productId, e.getMessage());
        }
    }

    private String buildMetadataJson(Long generationId, Long productId, String idempotencyKey,
        String productName, String howMade, String careTips) {
        try {
            AiJobMetadata metadata = new AiJobMetadata(
                productId.toString(),
                idempotencyKey,
                DEFAULT_TEMPLATE_ID,
                DEFAULT_LOCALE,
                new UserHints(productName, howMade, careTips),
                new GenerationOptions(generationId.toString())
            );
            return objectMapper.writeValueAsString(metadata);
        } catch (Exception e) {
            throw new RestClientException("AI job metadata 직렬화 실패", e);
        }
    }

    private byte[] fetchFirstImage(List<String> images, Long generationId) {
        if (images == null || images.isEmpty()) {
            log.warn("이미지 없이 AI job 제출 generationId={}", generationId);
            return new byte[0];
        }
        try {
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(images.getFirst()))
                .GET()
                .build();
            return httpClient.send(req, HttpResponse.BodyHandlers.ofByteArray()).body();
        } catch (Exception e) {
            log.warn("이미지 fetch 실패 generationId={} url={} reason={}", generationId, images.getFirst(), e.getMessage());
            return new byte[0];
        }
    }

    private record AiJobMetadata(
        @JsonProperty("product_id") String productId,
        @JsonProperty("idempotency_key") String idempotencyKey,
        @JsonProperty("template_id") String templateId,
        String locale,
        @JsonProperty("user_hints") UserHints userHints,
        GenerationOptions options
    ) {}

    private record UserHints(
        @JsonProperty("product_name") String productName,
        @JsonProperty("making_method") String makingMethod,
        @JsonProperty("care_guide") String careGuide
    ) {}

    private record GenerationOptions(
        @JsonProperty("source_generation_id") String sourceGenerationId
    ) {}

    private record AiJobAcceptedResponse(
        @JsonProperty("product_id") String productId,
        @JsonProperty("job_id") String jobId,
        @JsonProperty("request_id") String requestId,
        String status,
        @JsonProperty("status_url") String statusUrl,
        @JsonProperty("created_at") OffsetDateTime createdAt
    ) {}

    private static final class NamedByteArrayResource extends ByteArrayResource {
        private final String filename;

        NamedByteArrayResource(byte[] byteArray, String filename) {
            super(byteArray);
            this.filename = filename;
        }

        @Override
        public String getFilename() {
            return filename;
        }
    }
}
