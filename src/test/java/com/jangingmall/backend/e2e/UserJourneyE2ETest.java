package com.jangingmall.backend.e2e;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jangingmall.backend.global.security.JwtProperties;
import com.jangingmall.backend.global.security.JwtTokenProvider;
import com.jangingmall.backend.member.domain.MemberRole;
import com.jangingmall.backend.member.presentation.dto.MemberAccountRequests;
import com.jangingmall.backend.member.presentation.dto.MemberSignupRequest;
import com.jangingmall.backend.payment.domain.PaymentMethod;
import com.jangingmall.backend.payment.presentation.PaymentController;
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
class UserJourneyE2ETest {

    @LocalServerPort
    private int port;

    @Autowired
    private JwtProperties jwtProperties;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private String artisanToken;
    private String userToken;
    private Long userId;

    private static final Long ARTISAN_ID = 1L;
    private static final String TEST_EMAIL_PREFIX = "e2e-test-";

    record OrderResult(Long orderId, String orderNumber) {}

    @BeforeEach
    void setUp() throws Exception {
        JwtTokenProvider jwtTokenProvider = new JwtTokenProvider(jwtProperties);
        artisanToken = jwtTokenProvider.createAccessToken(ARTISAN_ID, MemberRole.ARTISAN);

        String email = TEST_EMAIL_PREFIX + System.currentTimeMillis() + "@test.com";
        userId = signup(email, "password123!", "홍길동", "01012345678");
        userToken = jwtTokenProvider.createAccessToken(userId, MemberRole.USER);
    }

    // ── HTTP helpers ──────────────────────────────────────────────

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

    private HttpResponse<String> get(String path, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl() + path))
            .GET();
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

    private HttpResponse<String> delete(String path, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl() + path))
            .DELETE();
        if (token != null) {
            builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return newClient().send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> data(HttpResponse<String> res) throws Exception {
        return (Map<String, Object>) objectMapper.readValue(res.body(), Map.class).get("data");
    }

    private Long longVal(Map<String, Object> map, String key) {
        return Long.valueOf(map.get(key).toString());
    }

    // ── Fixture helpers ──────────────────────────────────────────

    private Long signup(String email, String password, String name, String phone) throws Exception {
        MemberSignupRequest req = new MemberSignupRequest(
            email, password, password, name, phone, MemberRole.USER,
            new MemberSignupRequest.Agreements(true, true, true, false)
        );
        HttpResponse<String> res = post("/api/member/signup", req, null);
        assertThat(res.statusCode()).isEqualTo(201);
        // 응답 구조: ApiResponse<MemberSignupResponse> → data.member.memberId
        @SuppressWarnings("unchecked")
        Map<String, Object> member = (Map<String, Object>) data(res).get("member");
        return longVal(member, "memberId");
    }

    private Long createProduct() throws Exception {
        HttpResponse<String> res = post("/api/products",
            new ProductRequest.Create(null, null, "청자 다완", "설명", 85000, 10, null, List.of(), List.of(), null, List.of()),
            artisanToken);
        assertThat(res.statusCode()).isEqualTo(201);
        Long productId = longVal(data(res), "productId");

        HttpResponse<String> statusRes = patch(
            "/api/products/" + productId + "/status",
            new ProductRequest.ChangeStatus("ON_SALE"),
            artisanToken
        );
        assertThat(statusRes.statusCode()).isBetween(200, 204);
        return productId;
    }

    private Long addAddress(String token) throws Exception {
        MemberAccountRequests.CreateAddress req = new MemberAccountRequests.CreateAddress(
            "홍길동", "01012345678", "06236", "서울시 강남구 테헤란로 1", "101호", true
        );
        HttpResponse<String> res = post("/api/member/me/addresses", req, token);
        assertThat(res.statusCode()).isEqualTo(201);
        // AddressController.create()는 ResponseEntity<AddressData> — ApiResponse 래퍼 없음
        @SuppressWarnings("unchecked")
        Map<String, Object> body = objectMapper.readValue(res.body(), Map.class);
        return longVal(body, "addressId");
    }

    private Long addCartItem(String token, Long productId) throws Exception {
        PaymentController.CartItemRequest req = new PaymentController.CartItemRequest(
            productId, 1, List.of(), List.of()
        );
        HttpResponse<String> res = post("/api/payments/cart/items", req, token);
        assertThat(res.statusCode()).isEqualTo(201);
        @SuppressWarnings("unchecked")
        Map<String, Object> cart = data(res);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> sections = (List<Map<String, Object>>) cart.get("sections");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) sections.get(0).get("items");
        return longVal(items.get(0), "cartItemId");
    }

    private OrderResult createOrder(String token, Long cartItemId, Long addressId) throws Exception {
        PaymentController.CreateOrderRequest req = new PaymentController.CreateOrderRequest(
            List.of(cartItemId), addressId, null, PaymentMethod.CARD
        );
        HttpResponse<String> res = post("/api/payments/orders", req, token);
        assertThat(res.statusCode()).isEqualTo(201);
        Map<String, Object> orderData = data(res);
        Long orderId = longVal(orderData, "orderId");
        String orderNumber = (String) orderData.get("orderNumber");
        return new OrderResult(orderId, orderNumber);
    }

    // 실제 결제 PG 없이 상태를 PAID로 전환: toss webhook 시뮬레이션
    private void simulatePaid(String orderNumber) throws Exception {
        PaymentController.TossWebhookRequest webhook = new PaymentController.TossWebhookRequest(
            "PAYMENT_STATUS_CHANGED",
            new PaymentController.TossPaymentData("payKey-" + orderNumber, orderNumber, 85000L, "DONE")
        );
        HttpResponse<String> res = post("/api/payments/webhooks/toss", webhook, null);
        assertThat(res.statusCode()).isEqualTo(200);
    }

    // ── 테스트 메서드는 Task 3~7에서 추가됨 ──────────────────────────

    @Test
    @DisplayName("스캐폴딩 확인 — 컨텍스트가 정상 로드된다")
    void contextLoads() {
        assertThat(port).isGreaterThan(0);
    }

    @Test
    @DisplayName("키워드 검색 — 등록된 상품이 검색 결과에 포함된다")
    void keywordSearchReturnsProduct() throws Exception {
        Long productId = createProduct();

        HttpResponse<String> res = get("/api/products?keyword=청자", userToken);
        assertThat(res.statusCode()).isEqualTo(200);

        Map<String, Object> body = objectMapper.readValue(res.body(), Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> pageData = (Map<String, Object>) body.get("data");
        @SuppressWarnings("unchecked")
        List<?> items = (List<?>) pageData.get("content");
        assertThat(items).isNotEmpty();
    }

    @Test
    @DisplayName("키워드 검색 — 매칭 없으면 빈 목록이 반환된다")
    void keywordSearchNoMatch() throws Exception {
        HttpResponse<String> res = get("/api/products?keyword=존재하지않는상품XYZ", null);
        assertThat(res.statusCode()).isEqualTo(200);

        Map<String, Object> body = objectMapper.readValue(res.body(), Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> pageData = (Map<String, Object>) body.get("data");
        @SuppressWarnings("unchecked")
        List<?> items = (List<?>) pageData.get("content");
        assertThat(items).isEmpty();
    }

    @Test
    @DisplayName("상품 상세 조회 — ON_SALE 상품은 인증 없이 조회된다")
    void productDetailPublicAccess() throws Exception {
        Long productId = createProduct();

        HttpResponse<String> res = get("/api/products/" + productId, null);
        assertThat(res.statusCode()).isEqualTo(200);

        Map<String, Object> productData = data(res);
        assertThat(productData.get("productId").toString()).isEqualTo(productId.toString());
        assertThat(productData.get("status")).isEqualTo("ON_SALE");
    }
}
