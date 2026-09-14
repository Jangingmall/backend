package com.jangingmall.backend.content.infrastructure;

import com.jangingmall.backend.content.domain.AiContentClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
class RestAiContentClient implements AiContentClient {

    private final RestClient restClient;

    RestAiContentClient(
        @Value("${ai.base-url}") String baseUrl,
        @Value("${ai.timeout-seconds:30}") int timeoutSeconds
    ) {
        Duration timeout = Duration.ofSeconds(timeoutSeconds);
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
            HttpClient.newBuilder().connectTimeout(timeout).build()
        );
        factory.setReadTimeout(timeout);
        this.restClient = RestClient.builder()
            .baseUrl(baseUrl)
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
}
