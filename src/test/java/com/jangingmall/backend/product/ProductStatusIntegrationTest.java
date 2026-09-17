package com.jangingmall.backend.product;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jangingmall.backend.global.security.JwtProperties;
import com.jangingmall.backend.global.security.JwtTokenProvider;
import com.jangingmall.backend.member.domain.MemberRole;
import com.jangingmall.backend.product.presentation.ProductRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("local")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "management.server.port=-1")
class ProductStatusIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private JwtProperties jwtProperties;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private String artisanToken;
    private static final Long ARTISAN_ID = 1L;

    @BeforeEach
    void setUp() {
        JwtTokenProvider jwtTokenProvider = new JwtTokenProvider(jwtProperties);
        artisanToken = jwtTokenProvider.createAccessToken(ARTISAN_ID, MemberRole.ARTISAN);
    }

    private HttpClient newClient() {
        return HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .version(HttpClient.Version.HTTP_1_1)
            .build();
    }

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    private HttpResponse<String> post(String path, Object body, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl() + path))
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)));
        if (token != null) {
            builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return newClient().send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> patch(String path, Object body, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl() + path))
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .method("PATCH", HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)));
        if (token != null) {
            builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return newClient().send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl() + path))
            .GET();
        if (token != null) {
            builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return newClient().send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private Long createProduct() throws Exception {
        HttpResponse<String> res = post("/api/products",
            new ProductRequest.Create(null, null, "청자 다완", "설명", 85000, 10, null, List.of(), List.of(), null, List.of()),
            artisanToken);
        assertThat(res.statusCode()).isEqualTo(201);
        Map<?, ?> data = (Map<?, ?>) objectMapper.readValue(res.body(), Map.class).get("data");
        return Long.valueOf(data.get("productId").toString());
    }

    private void changeStatus(Long productId, String status, String token) throws Exception {
        HttpResponse<String> res = patch("/api/products/" + productId + "/status",
            new ProductRequest.ChangeStatus(status), token);
        assertThat(res.statusCode()).isBetween(200, 204);
    }

    private String getStatus(Long productId) throws Exception {
        HttpResponse<String> res = get("/api/products/" + productId, artisanToken);
        Map<?, ?> data = (Map<?, ?>) objectMapper.readValue(res.body(), Map.class).get("data");
        return (String) data.get("status");
    }

    @Test
    @DisplayName("상품 상태 전이 — DRAFT 생성 후 ON_SALE, HIDDEN, DRAFT 전이가 순서대로 성공한다")
    void productStatusTransition() throws Exception {
        Long productId = createProduct();
        assertThat(getStatus(productId)).isEqualTo("DRAFT");

        changeStatus(productId, "ON_SALE", artisanToken);
        assertThat(getStatus(productId)).isEqualTo("ON_SALE");

        changeStatus(productId, "HIDDEN", artisanToken);
        assertThat(getStatus(productId)).isEqualTo("HIDDEN");

        changeStatus(productId, "DRAFT", artisanToken);
        assertThat(getStatus(productId)).isEqualTo("DRAFT");
    }

    @Test
    @DisplayName("상품 상태 전이 — DRAFT에서 SOLD_OUT으로 직접 전이하면 422를 반환한다")
    void productStatusInvalidTransition() throws Exception {
        Long productId = createProduct();
        HttpResponse<String> res = patch("/api/products/" + productId + "/status",
            new ProductRequest.ChangeStatus("SOLD_OUT"), artisanToken);
        assertThat(res.statusCode()).isEqualTo(422);
    }

    @Test
    @DisplayName("상품 상태 변경 — 타 장인이 변경하면 403을 반환한다")
    void productStatusForbiddenByOtherArtisan() throws Exception {
        Long productId = createProduct();

        JwtTokenProvider jwtTokenProvider = new JwtTokenProvider(jwtProperties);
        String otherToken = jwtTokenProvider.createAccessToken(999L, MemberRole.ARTISAN);

        HttpResponse<String> res = patch("/api/products/" + productId + "/status",
            new ProductRequest.ChangeStatus("ON_SALE"), otherToken);
        assertThat(res.statusCode()).isEqualTo(403);
    }
}
