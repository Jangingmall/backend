package com.jangingmall.backend.e2e;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jangingmall.backend.global.security.JwtProperties;
import com.jangingmall.backend.global.security.JwtTokenProvider;
import com.jangingmall.backend.member.domain.MemberRole;
import com.jangingmall.backend.member.presentation.dto.MemberAccountRequests;
import com.jangingmall.backend.member.presentation.dto.MemberSignupRequest;
import com.jangingmall.backend.payment.application.PaymentGateway;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("local-postgresql")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "management.server.port=-1")
class UserJourneyE2ETest {

    @LocalServerPort
    private int port;

    @Autowired
    private JwtProperties jwtProperties;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StubPaymentGateway stubPaymentGateway;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private String artisanToken;
    private String userToken;
    private Long userId;

    private static final Long ARTISAN_ID = 1L;
    private static final String TEST_EMAIL_PREFIX = "e2e-test-";

    record OrderResult(Long orderId, String orderNumber, long totalAmount) {}

    @BeforeEach
    void setUp() throws Exception {
        stubPaymentGateway.reset();
        JwtTokenProvider jwtTokenProvider = new JwtTokenProvider(jwtProperties);
        artisanToken = jwtTokenProvider.createAccessToken(ARTISAN_ID, MemberRole.ARTISAN);

        String email = TEST_EMAIL_PREFIX + System.currentTimeMillis() + "@test.com";
        userId = signup(email, "password123!", "홍길동", "01012345678");
        jdbcTemplate.update("update member set status = 'ACTIVE' where member_id = ?", userId);
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
        return longVal(data(res), "addressId");
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
        long totalAmount = longVal(orderData, "totalAmount");
        return new OrderResult(orderId, orderNumber, totalAmount);
    }

    // 실제 결제 PG 없이 상태를 PAID로 전환: prepare → stub 등록 → webhook
    private void simulatePaid(Long orderId, String orderNumber, long amount) throws Exception {
        PaymentController.PreparePaymentRequest prepReq = new PaymentController.PreparePaymentRequest(
            orderId, amount, PaymentMethod.CARD
        );
        HttpResponse<String> prepRes = post("/api/payments", prepReq, userToken);
        assertThat(prepRes.statusCode()).isBetween(200, 201);

        String paymentKey = "stub-pay-" + orderNumber;
        stubPaymentGateway.register(paymentKey, orderNumber, amount, "DONE");

        PaymentController.TossWebhookRequest webhook = new PaymentController.TossWebhookRequest(
            "PAYMENT_STATUS_CHANGED",
            new PaymentController.TossPaymentData(paymentKey, orderNumber, amount, "DONE")
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

    @Test
    @DisplayName("장바구니 담기 — 상품이 장바구니에 추가된다")
    void addToCart() throws Exception {
        Long productId = createProduct();

        PaymentController.CartItemRequest req = new PaymentController.CartItemRequest(
            productId, 2, List.of(), List.of()
        );
        HttpResponse<String> res = post("/api/payments/cart/items", req, userToken);
        assertThat(res.statusCode()).isEqualTo(201);

        Map<String, Object> cart = data(res);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> sections = (List<Map<String, Object>>) cart.get("sections");
        assertThat(sections).isNotEmpty();
    }

    @Test
    @DisplayName("주문 생성 — 장바구니 상품으로 CREATED 상태 주문이 생성된다")
    void createOrderFromCart() throws Exception {
        Long productId = createProduct();
        Long cartItemId = addCartItem(userToken, productId);
        Long addressId = addAddress(userToken);

        PaymentController.CreateOrderRequest req = new PaymentController.CreateOrderRequest(
            List.of(cartItemId), addressId, "문 앞에 놓아주세요", PaymentMethod.CARD
        );
        HttpResponse<String> res = post("/api/payments/orders", req, userToken);
        assertThat(res.statusCode()).isEqualTo(201);

        Map<String, Object> orderData = data(res);
        assertThat(orderData.get("status")).isEqualTo("CREATED");
        assertThat(orderData).containsKey("orderId");
    }

    @Test
    @DisplayName("주문 생성 — 배송지 없이 요청하면 400이 반환된다")
    void createOrderWithoutAddress() throws Exception {
        Long productId = createProduct();
        Long cartItemId = addCartItem(userToken, productId);

        PaymentController.CreateOrderRequest req = new PaymentController.CreateOrderRequest(
            List.of(cartItemId), 0L, null, PaymentMethod.CARD
        );
        HttpResponse<String> res = post("/api/payments/orders", req, userToken);
        assertThat(res.statusCode()).isBetween(400, 422);
    }

    @Test
    @DisplayName("결제 웹훅 — DONE 웹훅이 수신되면 주문 상태가 PAID로 전환된다")
    void webhookTransitionsOrderToPaid() throws Exception {
        Long productId = createProduct();
        Long cartItemId = addCartItem(userToken, productId);
        Long addressId = addAddress(userToken);
        OrderResult order = createOrder(userToken, cartItemId, addressId);

        simulatePaid(order.orderId(), order.orderNumber(), order.totalAmount());

        HttpResponse<String> res = get("/api/member/me/orders/" + order.orderId(), userToken);
        assertThat(res.statusCode()).isEqualTo(200);
        Map<String, Object> orderData = data(res);
        assertThat(orderData.get("status")).isEqualTo("PAID");
    }

    @Test
    @DisplayName("결제 미확정 — 웹훅 없이 주문은 CREATED 상태를 유지한다")
    void orderRemainsCreatedWithoutConfirm() throws Exception {
        Long productId = createProduct();
        Long cartItemId = addCartItem(userToken, productId);
        Long addressId = addAddress(userToken);
        OrderResult order = createOrder(userToken, cartItemId, addressId);

        HttpResponse<String> res = get("/api/member/me/orders/" + order.orderId(), userToken);
        assertThat(res.statusCode()).isEqualTo(200);
        Map<String, Object> orderData = data(res);
        assertThat(orderData.get("status")).isEqualTo("CREATED");
    }

    @Test
    @DisplayName("결제 금액 불일치 — 잘못된 금액으로 confirm 요청 시 오류가 반환된다")
    void confirmWithWrongAmountReturnsMismatch() throws Exception {
        Long productId = createProduct();
        Long cartItemId = addCartItem(userToken, productId);
        Long addressId = addAddress(userToken);
        OrderResult order = createOrder(userToken, cartItemId, addressId);

        PaymentController.PreparePaymentRequest prepReq = new PaymentController.PreparePaymentRequest(
            order.orderId(), order.totalAmount(), PaymentMethod.CARD
        );
        HttpResponse<String> prepRes = post("/api/payments", prepReq, userToken);
        assertThat(prepRes.statusCode()).isBetween(200, 201);

        String paymentKey = "stub-pay-" + order.orderNumber();
        stubPaymentGateway.register(paymentKey, order.orderNumber(), order.totalAmount(), "DONE");

        PaymentController.ConfirmPaymentRequest confirmReq = new PaymentController.ConfirmPaymentRequest(
            paymentKey, order.orderNumber(), order.totalAmount() + 1L
        );
        HttpResponse<String> res = post("/api/payments/confirm", confirmReq, userToken);
        assertThat(res.statusCode()).isBetween(400, 422);
    }
}
