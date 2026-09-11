package com.jangingmall.backend.payment.presentation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jangingmall.backend.global.config.SecurityConfig;
import com.jangingmall.backend.global.exception.BusinessRuleViolationException;
import com.jangingmall.backend.global.exception.DomainException;
import com.jangingmall.backend.global.exception.ErrorCode;
import com.jangingmall.backend.global.exception.GlobalExceptionHandler;
import com.jangingmall.backend.payment.application.CartService;
import com.jangingmall.backend.payment.application.DeliveryService;
import com.jangingmall.backend.payment.application.PaymentProfileService;
import com.jangingmall.backend.payment.application.PaymentService;
import com.jangingmall.backend.payment.application.ReturnService;
import com.jangingmall.backend.payment.domain.OrderStatus;
import com.jangingmall.backend.payment.domain.PaymentMethod;
import com.jangingmall.backend.payment.domain.PaymentStatus;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PaymentController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class PaymentControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private CartService carts;
    @MockitoBean private PaymentService payments;
    @MockitoBean private PaymentProfileService paymentProfiles;
    @MockitoBean private DeliveryService deliveries;
    @MockitoBean private ReturnService returns;

    @Test
    @DisplayName("PAY-P2-001/002 결제수단 목록 조회는 200과 목록 또는 빈 배열을 반환한다")
    void getsPaymentMethods() throws Exception {
        when(paymentProfiles.methods(1L)).thenReturn(List.of(new PaymentProfileService.PaymentMethodData(
            10L, PaymentMethod.CARD, "VISA", "424242******4242", true)));

        mockMvc.perform(get("/api/payments/methods").with(user()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].paymentMethodId").value(10))
            .andExpect(jsonPath("$.data[0].isDefault").value(true));

        when(paymentProfiles.methods(1L)).thenReturn(List.of());
        mockMvc.perform(get("/api/payments/methods").with(user()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    @DisplayName("PAY-P2-003/009/013 결제수단 API는 JWT 없으면 401이다")
    void protectsPaymentMethodEndpoints() throws Exception {
        mockMvc.perform(get("/api/payments/methods")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/payments/methods").contentType(APPLICATION_JSON).content(validCard()))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/payments/methods/10")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("PAY-P2-004 결제수단 조회 서버 오류는 500 INTERNAL_ERROR다")
    void mapsPaymentMethodServerError() throws Exception {
        when(paymentProfiles.methods(1L)).thenThrow(new IllegalStateException("DB down"));
        mockMvc.perform(get("/api/payments/methods").with(user()))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.errorCode").value("INTERNAL_ERROR"));
    }

    @Test
    @DisplayName("PAY-P2-005 정상 결제수단 등록은 201 Created다")
    void registersPaymentMethod() throws Exception {
        when(paymentProfiles.registerMethod(eq(1L), any())).thenReturn(new PaymentProfileService.PaymentMethodData(
            10L, PaymentMethod.CARD, "VISA", "424242******4242", true));
        mockMvc.perform(post("/api/payments/methods").with(user()).contentType(APPLICATION_JSON).content(validCard()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value(201))
            .andExpect(jsonPath("$.data.paymentMethodId").value(10));
    }

    @Test
    @DisplayName("PAY-P2-006/007 결제수단 필수값 누락과 잘못된 형식은 400 INVALID_INPUT이다")
    void validatesPaymentMethodRequest() throws Exception {
        mockMvc.perform(post("/api/payments/methods").with(user()).contentType(APPLICATION_JSON)
                .content("{\"type\":\"CARD\"}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("INVALID_INPUT"));
        mockMvc.perform(post("/api/payments/methods").with(user()).contentType(APPLICATION_JSON)
                .content("{\"type\":\"CARD\",\"cardNumber\":\"abc\",\"expiry\":\"99/20\",\"birthOrBusinessNo\":\"1\"}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("PAY-P2-008 중복 결제수단 등록은 409 CONFLICT다")
    void rejectsDuplicatePaymentMethod() throws Exception {
        when(paymentProfiles.registerMethod(eq(1L), any())).thenThrow(new DomainException(ErrorCode.CONFLICT));
        mockMvc.perform(post("/api/payments/methods").with(user()).contentType(APPLICATION_JSON).content(validCard()))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("CONFLICT"));
    }

    @Test
    @DisplayName("PAY-P2-010/011/012 결제수단 삭제는 200이며 없거나 타인 소유면 404다")
    void deletesPaymentMethod() throws Exception {
        mockMvc.perform(delete("/api/payments/methods/10").with(user()))
            .andExpect(status().isOk());
        doThrow(new DomainException(ErrorCode.NOT_FOUND)).when(paymentProfiles).deleteMethod(1L, 11L);
        mockMvc.perform(delete("/api/payments/methods/11").with(user()))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("PAY-P2-014 정상 환불계좌 등록은 201 Created다")
    void registersRefundAccount() throws Exception {
        when(paymentProfiles.registerRefundAccount(eq(1L), any())).thenReturn(
            new PaymentProfileService.RefundAccountData(20L, "국민은행", "********9012", "홍길동"));
        mockMvc.perform(post("/api/payments/refund-account").with(user()).contentType(APPLICATION_JSON)
                .content(validRefundAccount()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.refundAccountId").value(20));
    }

    @Test
    @DisplayName("PAY-P2-015/016 환불계좌 필수값 누락과 형식 오류는 400이다")
    void validatesRefundAccountRequest() throws Exception {
        mockMvc.perform(post("/api/payments/refund-account").with(user()).contentType(APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("INVALID_INPUT"));
        mockMvc.perform(post("/api/payments/refund-account").with(user()).contentType(APPLICATION_JSON)
                .content("{\"bankName\":\"!\",\"accountNumber\":\"abc\",\"accountHolder\":\"1\"}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("PAY-P2-017/018 환불계좌 API는 JWT 없으면 401, 서버 오류면 500이다")
    void protectsAndMapsRefundAccountErrors() throws Exception {
        mockMvc.perform(post("/api/payments/refund-account").contentType(APPLICATION_JSON).content(validRefundAccount()))
            .andExpect(status().isUnauthorized());
        when(paymentProfiles.registerRefundAccount(eq(1L), any())).thenThrow(new IllegalStateException("DB down"));
        mockMvc.perform(post("/api/payments/refund-account").with(user()).contentType(APPLICATION_JSON)
                .content(validRefundAccount()))
            .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.errorCode").value("INTERNAL_ERROR"));
    }

    @Test
    @DisplayName("PAY-P0-001~005 장바구니 조회는 게스트 허용·빈 sections·DB 오류 500 계약을 지킨다")
    void getsPublicCartAndMapsServerError() throws Exception {
        when(carts.get(null, "guest-1")).thenReturn(emptyCart());
        mockMvc.perform(get("/api/payments/cart").cookie(new Cookie("guestCartId", "guest-1")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.sections").isEmpty());
        when(carts.get(null, null)).thenThrow(new IllegalStateException("DB down"));
        mockMvc.perform(get("/api/payments/cart"))
            .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.errorCode").value("INTERNAL_ERROR"));
    }

    @Test
    @DisplayName("PAY-P0-006/011 장바구니 정상 추가는 201+guest 쿠키, 잘못된 수량은 400이다")
    void createsGuestCartAndValidatesQuantity() throws Exception {
        when(carts.add(eq(null), eq(null), any())).thenReturn(new CartService.CartMutation(emptyCart(), "guest-new"));
        mockMvc.perform(post("/api/payments/cart/items").contentType(APPLICATION_JSON)
                .content("{\"productId\":7,\"quantity\":1}"))
            .andExpect(status().isCreated())
            .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("guestCartId=guest-new")));
        mockMvc.perform(post("/api/payments/cart/items").contentType(APPLICATION_JSON)
                .content("{\"productId\":7,\"quantity\":0}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("PAY-P0-015 수량 0 이하와 PAY-P0-029 guestCartId 없는 병합은 400이다")
    void validatesCartMutationRequests() throws Exception {
        mockMvc.perform(patch("/api/payments/cart/items/10").contentType(APPLICATION_JSON)
                .content("{\"quantity\":0}"))
            .andExpect(status().isBadRequest());
        when(carts.merge(1L, null)).thenThrow(new DomainException(ErrorCode.INVALID_INPUT));
        mockMvc.perform(post("/api/payments/cart/merge").with(user()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("PAY-P0-026 게스트 장바구니 병합 성공 시 guestCartId 쿠키를 만료시킨다")
    void expiresGuestCookieAfterMerge() throws Exception {
        when(carts.merge(1L, "guest-1")).thenReturn(new CartService.CartMutation(emptyCart(), null));
        mockMvc.perform(post("/api/payments/cart/merge").with(user())
                .cookie(new Cookie("guestCartId", "guest-1")))
            .andExpect(status().isOk())
            .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.allOf(
                org.hamcrest.Matchers.containsString("guestCartId="),
                org.hamcrest.Matchers.containsString("Max-Age=0"))));
    }

    @Test
    @DisplayName("PAY-P0-030 장바구니 병합은 JWT 없으면 401이다")
    void protectsCartMerge() throws Exception {
        mockMvc.perform(post("/api/payments/cart/merge"))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("PAY-P0-031 정상 주문 생성은 201 Created다")
    void createsOrder() throws Exception {
        PaymentService.OrderData data = new PaymentService.OrderData(100L, "ORD-000001", 25_000L,
            OrderStatus.CREATED, Instant.now(), List.of());
        when(payments.createOrder(eq(1L), any(), eq("checkout-1"))).thenReturn(data);
        mockMvc.perform(post("/api/payments/orders").with(user()).header("Idempotency-Key", "checkout-1")
                .contentType(APPLICATION_JSON).content(validOrder()))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.data.orderId").value(100));
    }

    @Test
    @DisplayName("PAY-P0-032 빈 장바구니 주문은 422, PAY-P0-036 배송지 누락은 400이다")
    void mapsOrderValidation() throws Exception {
        when(payments.createOrder(eq(1L), any(), eq(null))).thenThrow(new BusinessRuleViolationException("빈 장바구니"));
        mockMvc.perform(post("/api/payments/orders").with(user()).contentType(APPLICATION_JSON)
                .content("{\"cartItemIds\":[],\"addressId\":9,\"paymentMethod\":\"CARD\"}"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.errorCode").value("BUSINESS_RULE_VIOLATION"));
        mockMvc.perform(post("/api/payments/orders").with(user()).contentType(APPLICATION_JSON)
                .content("{\"cartItemIds\":[1],\"paymentMethod\":\"CARD\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PAY-P0-037/044/030/007 보호된 주문·결제·배송 API는 JWT 없으면 401이다")
    void protectsCheckoutEndpoints() throws Exception {
        mockMvc.perform(post("/api/payments/orders").contentType(APPLICATION_JSON).content(validOrder()))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/payments/confirm").contentType(APPLICATION_JSON)
                .content("{\"paymentKey\":\"pay_key\",\"orderId\":\"ORD-000001\",\"amount\":25000}"))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/payments/orders/100/delivery")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("PAY-P0-039/042 결제 준비는 신규 201·기존 READY 200을 구분한다")
    void preparesPaymentWithCreatedSemantics() throws Exception {
        PaymentService.PreparedPayment created = new PaymentService.PreparedPayment(200L, 100L, 25_000L, "ck", true);
        PaymentService.PreparedPayment reused = new PaymentService.PreparedPayment(200L, 100L, 25_000L, "ck", false);
        when(payments.prepare(eq(1L), any())).thenReturn(created, reused);
        String body = "{\"orderId\":100,\"amount\":25000,\"paymentMethod\":\"CARD\"}";
        mockMvc.perform(post("/api/payments").with(user()).contentType(APPLICATION_JSON).content(body))
            .andExpect(status().isCreated());
        mockMvc.perform(post("/api/payments").with(user()).contentType(APPLICATION_JSON).content(body))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PAY-P0-044 paymentKey 누락은 400, PAY-P0-045 orderId 불일치는 422다")
    void validatesConfirmation() throws Exception {
        mockMvc.perform(post("/api/payments/confirm").with(user()).contentType(APPLICATION_JSON)
                .content("{\"orderId\":\"ORD-000001\",\"amount\":25000}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("INVALID_INPUT"));
        when(payments.confirm(eq(1L), any())).thenThrow(new BusinessRuleViolationException("orderId 불일치"));
        mockMvc.perform(post("/api/payments/confirm").with(user()).contentType(APPLICATION_JSON)
                .content("{\"paymentKey\":\"pay_key\",\"orderId\":\"ORD-WRONG\",\"amount\":25000}"))
            .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("PAY-P0-051~054 실패 처리 API는 정상 200·없는 대상 404·JWT 없음 401이다")
    void mapsPaymentFailureEndpoint() throws Exception {
        String body = "{\"orderId\":\"ORD-000001\",\"errorCode\":\"REJECTED\",\"errorMessage\":\"거절\"}";
        mockMvc.perform(post("/api/payments/fail").with(user()).contentType(APPLICATION_JSON).content(body))
            .andExpect(status().isOk());
        mockMvc.perform(post("/api/payments/fail").contentType(APPLICATION_JSON).content(body))
            .andExpect(status().isUnauthorized());
        doThrow(new DomainException(ErrorCode.NOT_FOUND)).when(payments).fail(eq(1L), any());
        mockMvc.perform(post("/api/payments/fail").with(user()).contentType(APPLICATION_JSON).content(body))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PAY-P0-055~061 취소 API는 200과 도메인 오류 상태를 전달한다")
    void mapsCancellationEndpoint() throws Exception {
        PaymentService.PaymentData data = new PaymentService.PaymentData(200L, 100L, "ORD-000001", 25_000L,
            PaymentMethod.CARD, PaymentStatus.CANCELED, Instant.now(), Instant.now());
        when(payments.cancel(1L, 200L, "취소")).thenReturn(data);
        mockMvc.perform(post("/api/payments/200/cancel").with(user()).contentType(APPLICATION_JSON)
                .content("{\"reason\":\"취소\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("CANCELED"));
        mockMvc.perform(post("/api/payments/200/cancel").contentType(APPLICATION_JSON)
                .content("{\"reason\":\"취소\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("PAY-P1-003~007 배송조회는 200·404·401 계약을 전달한다")
    void mapsDeliveryEndpoint() throws Exception {
        when(deliveries.get(1L, 100L)).thenReturn(new DeliveryService.DeliveryData(
            100L, "CJ대한통운", "1234567890", com.jangingmall.backend.payment.domain.DeliveryStatus.IN_TRANSIT));
        mockMvc.perform(get("/api/payments/orders/100/delivery").with(user()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.trackingNumber").value("1234567890"));
        when(deliveries.get(1L, 101L)).thenThrow(new DomainException(ErrorCode.NOT_FOUND));
        mockMvc.perform(get("/api/payments/orders/101/delivery").with(user())).andExpect(status().isNotFound());
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor user() {
        return authentication(new UsernamePasswordAuthenticationToken(
            1L, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    private CartService.CartData emptyCart() {
        return new CartService.CartData(List.of(), 0L, 0L, 0);
    }

    private String validCard() {
        return "{\"type\":\"CARD\",\"cardNumber\":\"4242424242424242\",\"expiry\":\"12/30\",\"birthOrBusinessNo\":\"900101\"}";
    }

    private String validRefundAccount() {
        return "{\"bankName\":\"국민은행\",\"accountNumber\":\"123-456-789012\",\"accountHolder\":\"홍길동\"}";
    }

    private String validOrder() {
        return "{\"cartItemIds\":[1],\"addressId\":9,\"paymentMethod\":\"CARD\"}";
    }
}
