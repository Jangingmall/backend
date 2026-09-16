package com.jangingmall.backend.content.infrastructure;

import com.jangingmall.backend.content.domain.AiContentClient;
import com.jangingmall.backend.content.domain.AiProductSyncPayload;
import com.jangingmall.backend.content.domain.AiProductUpdatePayload;
import com.jangingmall.backend.global.config.AiProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
class RestAiContentClient implements AiContentClient {

    private final RestClient restClient;

    @Autowired
    RestAiContentClient(AiProperties aiProperties) {
        Duration timeout = Duration.ofSeconds(aiProperties.timeoutSeconds());
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
            HttpClient.newBuilder().connectTimeout(timeout).build()
        );
        factory.setReadTimeout(timeout);
        this.restClient = RestClient.builder()
            .baseUrl(aiProperties.ollamaUrl())
            .requestFactory(factory)
            .build();
    }

    @Override
    public String requestGeneration(Long generationId, Long productId, List<String> images, String productName, String howMade, String careTips) {
        Map<String, Object> body = Map.of(
            "generationId", generationId,
            "productId", productId,
            "images", images,
            "productName", productName,
            "howMade", howMade,
            "careTips", careTips
        );
        log.info("AI 콘텐츠 생성 요청 전송 generationId={} productId={}", generationId, productId);
        return restClient.post()
            .uri("/ai/products")
            .body(body)
            .retrieve()
            .body(String.class);
    }

    @Override
    public void syncProduct(AiProductSyncPayload payload) {
        try {
            restClient.post()
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
            restClient.put()
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
            restClient.delete()
                .uri("/ai/products/{id}", productId)
                .retrieve()
                .toBodilessEntity();
            log.info("AI 상품 삭제 동기화 완료 productId={}", productId);
        } catch (RestClientException e) {
            log.error("AI 상품 삭제 동기화 실패 productId={} reason={}", productId, e.getMessage());
        }
    }
}
