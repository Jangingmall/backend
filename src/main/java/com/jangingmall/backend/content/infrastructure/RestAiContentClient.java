package com.jangingmall.backend.content.infrastructure;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.jangingmall.backend.content.domain.AiContentClient;
import com.jangingmall.backend.content.domain.AiImageFetchException;
import com.jangingmall.backend.content.domain.AiJobAccepted;
import com.jangingmall.backend.content.domain.AiProductSyncPayload;
import com.jangingmall.backend.content.domain.AiProductUpdatePayload;
import com.jangingmall.backend.global.config.AiProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Component
@ConditionalOnProperty(name = "ai.base-url", matchIfMissing = false)
class RestAiContentClient implements AiContentClient {

    private static final String DETAIL_PAGE_JOBS_PATH = "/internal/v1/ai/detail-page-jobs";
    private static final String DETAIL_PAGE_RENDERS_PATH = "/internal/v1/ai/detail-page-renders";
    private static final String RENDER_IDEMPOTENCY_KEY_PREFIX = "render-";
    private static final String RENDER_IN_PROGRESS_DETAIL = "Approval is already in progress";
    private static final String AI_INTERNAL_TOKEN_HEADER = "X-AI-Internal-Token";
    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    private static final String DEFAULT_TEMPLATE_ID = "default-long-detail-page";
    private static final String DEFAULT_LOCALE = "ko-KR";
    /** AI 접수 계약: 대표 사진 포함 12장, 장당 10MB, 합계 120MB(초과 시 413). */
    private static final int MAX_ADDITIONAL_IMAGES = 11;
    private static final int MAX_IMAGE_BYTES = 10 * 1024 * 1024;
    private static final long MAX_TOTAL_IMAGE_BYTES = 120L * 1024 * 1024;

    private final RestClient generationClient;
    private final RestClient syncClient;
    private final String aiInternalAuthToken;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final Counter syncProductFailureCounter;
    private final Counter updateProductFailureCounter;
    private final Counter deleteProductFailureCounter;
    private final Counter imageFetchFailureCounter;

    RestAiContentClient(RestClient generationClient, RestClient syncClient,
        String aiInternalAuthToken, ObjectMapper objectMapper, HttpClient httpClient,
        MeterRegistry meterRegistry) {
        this.generationClient = generationClient;
        this.syncClient = syncClient;
        this.aiInternalAuthToken = aiInternalAuthToken;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
        this.syncProductFailureCounter = buildSyncFailureCounter(meterRegistry, "syncProduct");
        this.updateProductFailureCounter = buildSyncFailureCounter(meterRegistry, "updateProduct");
        this.deleteProductFailureCounter = buildSyncFailureCounter(meterRegistry, "deleteProduct");
        this.imageFetchFailureCounter = Counter.builder("ai_image_fetch_failure")
            .description("AI 이미지 fetch 실패 횟수")
            .register(meterRegistry);
    }

    @Autowired
    RestAiContentClient(AiProperties aiProperties, ObjectMapper objectMapper, MeterRegistry meterRegistry) {
        Duration timeout = Duration.ofSeconds(aiProperties.timeoutSeconds());
        this.httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(timeout)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(timeout);
        String generationBase = aiProperties.contentBaseUrl() != null
            ? aiProperties.contentBaseUrl()
            : aiProperties.baseUrl();
        this.generationClient = RestClient.builder()
            .baseUrl(generationBase)
            .requestFactory(factory)
            .build();
        this.syncClient = RestClient.builder()
            .baseUrl(aiProperties.baseUrl())
            .requestFactory(factory)
            .build();
        this.aiInternalAuthToken = aiProperties.internalAuthToken();
        this.objectMapper = objectMapper;
        this.syncProductFailureCounter = buildSyncFailureCounter(meterRegistry, "syncProduct");
        this.updateProductFailureCounter = buildSyncFailureCounter(meterRegistry, "updateProduct");
        this.deleteProductFailureCounter = buildSyncFailureCounter(meterRegistry, "deleteProduct");
        this.imageFetchFailureCounter = Counter.builder("ai_image_fetch_failure")
            .description("AI 이미지 fetch 실패 횟수")
            .register(meterRegistry);
    }

    private static Counter buildSyncFailureCounter(MeterRegistry registry, String operation) {
        return Counter.builder("ai_sync_failure")
            .tag("operation", operation)
            .description("AI 상품 동기화 실패 횟수")
            .register(registry);
    }

    @Override
    public AiJobAccepted submitJob(Long generationId, Long productId, List<String> images,
        String productName, String howMade, String careTips) {
        String idempotencyKey = generationId.toString();
        String metadataJson = buildMetadataJson(generationId, productId, idempotencyKey, productName, howMade, careTips);
        FetchedImage image = fetchFirstImage(images, generationId);
        List<FetchedImage> additionalImages = fetchAdditionalImages(images, generationId, image.data().length);

        // AI 서버는 part의 Content-Type과 실제 바이트 시그니처가 일치하는지 검증한다.
        // 파일명 확장자로 Content-Type이 정해지므로 실제 이미지 종류에 맞춰 명시한다.
        HttpHeaders imageHeaders = new HttpHeaders();
        imageHeaders.setContentType(MediaType.parseMediaType(image.mimeType()));
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("metadata", metadataJson);
        body.add("product_image", new HttpEntity<>(
            new NamedByteArrayResource(image.data(), "product_image." + image.extension()), imageHeaders));
        // 대표 사진(첫 장) 외 나머지 판매자 사진은 같은 이름의 파트를 반복해 보낸다. AI는 첫 장으로 상품을 분석하고
        // 나머지는 상세페이지에 실제 사진으로 쓴다.
        for (int i = 0; i < additionalImages.size(); i++) {
            FetchedImage extra = additionalImages.get(i);
            HttpHeaders extraHeaders = new HttpHeaders();
            extraHeaders.setContentType(MediaType.parseMediaType(extra.mimeType()));
            body.add("product_images", new HttpEntity<>(
                new NamedByteArrayResource(extra.data(), "product_image_" + (i + 2) + "." + extra.extension()), extraHeaders));
        }

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
    public String getJobStatus(String jobId) {
        AiJobStatusResponse response = generationClient.get()
            .uri("/internal/v1/ai/detail-page-jobs/{jobId}", jobId)
            .header(AI_INTERNAL_TOKEN_HEADER, aiInternalAuthToken)
            .retrieve()
            .body(AiJobStatusResponse.class);
        return response != null ? response.status() : null;
    }

    @Override
    public void approveRender(String jobId, Long generationId, Long productId) {
        JsonNode draft = fetchDraft(jobId);
        String idempotencyKey = RENDER_IDEMPOTENCY_KEY_PREFIX + generationId;
        // GenAI 계약: multipart 'metadata'(extra=forbid). draft는 AI가 만든 초안을 수정 없이 그대로 돌려준다.
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("product_id", productId.toString());
        metadata.put("idempotency_key", idempotencyKey);
        metadata.put("draft_id", jobId);
        metadata.put("draft", draft);
        metadata.put("options", new GenerationOptions(generationId.toString()));
        String metadataJson;
        try {
            metadataJson = objectMapper.writeValueAsString(metadata);
        } catch (Exception e) {
            throw new RestClientException("AI 렌더 metadata 직렬화 실패", e);
        }

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("metadata", metadataJson);
        log.info("AI 렌더 요청 generationId={} jobId={} productId={} idempotencyKey={}",
            generationId, jobId, productId, idempotencyKey);
        try {
            generationClient.post()
                .uri(DETAIL_PAGE_RENDERS_PATH)
                .header(AI_INTERNAL_TOKEN_HEADER, aiInternalAuthToken)
                .header(IDEMPOTENCY_KEY_HEADER, idempotencyKey)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve()
                .toBodilessEntity();
        } catch (HttpClientErrorException.Conflict e) {
            // 같은 멱등성 키로 이미 렌더 중이면 중복 요청이므로 결과를 기다리면 된다.
            if (e.getResponseBodyAsString().contains(RENDER_IN_PROGRESS_DETAIL)) {
                log.info("AI 렌더 이미 진행 중 generationId={} jobId={}", generationId, jobId);
                return;
            }
            throw e;
        }
        log.info("AI 렌더 요청 완료 generationId={} jobId={}", generationId, jobId);
    }

    /** AI 작업 상태 응답에서 사용자가 승인할 초안(draft.draft)을 꺼낸다. */
    private JsonNode fetchDraft(String jobId) {
        String statusJson = generationClient.get()
            .uri("/internal/v1/ai/detail-page-jobs/{jobId}", jobId)
            .header(AI_INTERNAL_TOKEN_HEADER, aiInternalAuthToken)
            .retrieve()
            .body(String.class);
        JsonNode draft;
        try {
            draft = statusJson == null ? null : objectMapper.readTree(statusJson).path("draft").path("draft");
        } catch (Exception e) {
            throw new RestClientException("AI 작업 상태 응답을 해석하지 못했습니다 jobId=" + jobId, e);
        }
        if (draft == null || draft.isMissingNode() || draft.isNull()) {
            throw new IllegalStateException("AI 작업 상태 응답에 초안(draft)이 없습니다 jobId=" + jobId);
        }
        return draft;
    }

    @Override
    public void syncProduct(AiProductSyncPayload payload) {
        try {
            syncClient.post()
                .uri("/ai/products")
                .body(payload)
                .retrieve()
                .toBodilessEntity();
            log.info("AI 상품 동기화 완료 productId={}", payload.product().product_id());
        } catch (RestClientException e) {
            syncProductFailureCounter.increment();
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
            updateProductFailureCounter.increment();
            log.error("AI 상품 수정 동기화 실패 productId={} reason={}", productId, e.getMessage());
        }
    }

    @Override
    public void updateProductStatus(Long productId, String status) {
        try {
            syncClient.put()
                .uri("/ai/products/{id}", productId)
                .body(Map.of("product", Map.of("status", status)))
                .retrieve()
                .toBodilessEntity();
            log.info("AI 상품 상태 동기화 완료 productId={} status={}", productId, status);
        } catch (RestClientException e) {
            updateProductFailureCounter.increment();
            log.error("AI 상품 상태 동기화 실패 productId={} reason={}", productId, e.getMessage());
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
            deleteProductFailureCounter.increment();
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

    private FetchedImage fetchFirstImage(List<String> images, Long generationId) {
        if (images == null || images.isEmpty()) {
            log.warn("이미지 없이 AI job 제출 generationId={}", generationId);
            return new FetchedImage(new byte[0], "image/jpeg", "jpg");
        }
        return fetchImage(images.getFirst(), generationId);
    }

    /**
     * 대표 사진 다음 사진들을 내려받는다. 첫 장만 있어도 상세페이지를 만들 수 있으므로 추가 사진의 실패·크기 초과는
     * 그 사진만 빼고 계속한다. 장수(대표 포함 12장)·장당 10MB·합계 120MB를 넘기면 AI가 413으로 거절하므로 미리 제외한다.
     */
    private List<FetchedImage> fetchAdditionalImages(List<String> images, Long generationId, int firstImageBytes) {
        if (images == null || images.size() < 2) {
            return List.of();
        }
        List<FetchedImage> fetched = new java.util.ArrayList<>();
        long totalBytes = firstImageBytes;
        List<String> candidates = images.subList(1, images.size());
        if (candidates.size() > MAX_ADDITIONAL_IMAGES) {
            log.warn("추가 사진이 상한을 넘어 일부를 제외합니다 generationId={} total={} max={}",
                generationId, images.size(), MAX_ADDITIONAL_IMAGES + 1);
        }
        for (String url : candidates.subList(0, Math.min(candidates.size(), MAX_ADDITIONAL_IMAGES))) {
            FetchedImage image;
            try {
                image = fetchImage(url, generationId);
            } catch (AiImageFetchException e) {
                log.warn("추가 사진을 제외하고 제출합니다 generationId={} url={} reason={}",
                    generationId, withoutQuery(url), e.getMessage());
                continue;
            }
            if (image.data().length > MAX_IMAGE_BYTES || totalBytes + image.data().length > MAX_TOTAL_IMAGE_BYTES) {
                log.warn("추가 사진이 크기 상한을 넘어 제외합니다 generationId={} url={} bytes={}",
                    generationId, withoutQuery(url), image.data().length);
                continue;
            }
            totalBytes += image.data().length;
            fetched.add(image);
        }
        return fetched;
    }

    private FetchedImage fetchImage(String url, Long generationId) {
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
                throw new IllegalArgumentException("http(s) URL이 아닙니다");
            }
            HttpResponse<byte[]> response = httpClient.send(
                HttpRequest.newBuilder().uri(uri).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
            int status = response.statusCode();
            String contentType = response.headers().firstValue("Content-Type").orElse("");
            byte[] body = response.body();
            if (status < 200 || status >= 300) {
                throw new IllegalStateException("HTTP " + status);
            }
            if (body == null || body.length == 0) {
                throw new IllegalStateException("빈 응답 본문");
            }
            if (!isImageContentType(contentType)) {
                throw new IllegalStateException("이미지가 아닌 Content-Type=" + contentType);
            }
            FetchedImage image = sniffImage(body);
            if (image == null) {
                throw new IllegalStateException("지원하지 않는 이미지 형식입니다 (PNG, JPEG, WebP만 가능)");
            }
            log.info("이미지 fetch 완료 generationId={} url={} status={} contentType={} detected={} bytes={}",
                generationId, withoutQuery(url), status, contentType, image.mimeType(), body.length);
            return image;
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            imageFetchFailureCounter.increment();
            log.warn("이미지 fetch 실패 generationId={} url={} reason={}", generationId, withoutQuery(url), e.getMessage());
            throw new AiImageFetchException("이미지를 내려받지 못했습니다: " + e.getMessage(), e);
        }
    }

    /** 바이트 시그니처로 AI 서버가 허용하는 이미지 종류(PNG, JPEG, WebP)를 판별한다. 그 외는 null. */
    static FetchedImage sniffImage(byte[] data) {
        if (data.length >= 8 && (data[0] & 0xFF) == 0x89 && data[1] == 'P' && data[2] == 'N' && data[3] == 'G'
            && data[4] == '\r' && data[5] == '\n' && data[6] == 0x1A && data[7] == '\n') {
            return new FetchedImage(data, "image/png", "png");
        }
        if (data.length >= 3 && (data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xD8 && (data[2] & 0xFF) == 0xFF) {
            return new FetchedImage(data, "image/jpeg", "jpg");
        }
        if (data.length >= 12 && data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F'
            && data[8] == 'W' && data[9] == 'E' && data[10] == 'B' && data[11] == 'P') {
            return new FetchedImage(data, "image/webp", "webp");
        }
        return null;
    }

    record FetchedImage(byte[] data, String mimeType, String extension) {}

    private static boolean isImageContentType(String contentType) {
        String type = contentType.toLowerCase(Locale.ROOT);
        return type.startsWith("image/") || type.startsWith("application/octet-stream");
    }

    /** 서명 URL의 쿼리스트링(서명값)이 로그에 남지 않도록 제거한다. */
    private static String withoutQuery(String url) {
        int query = url.indexOf('?');
        return query < 0 ? url : url.substring(0, query);
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

    private record AiJobStatusResponse(
        @JsonProperty("job_id") String jobId,
        String status,
        Integer progress
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
