package com.jangingmall.backend.payment.presentation;

import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.delete;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.patch;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import com.jangingmall.backend.global.config.SecurityConfig;
import com.jangingmall.backend.global.docs.RestDocsControllerTest;
import com.jangingmall.backend.global.exception.BusinessRuleViolationException;
import com.jangingmall.backend.global.exception.DomainException;
import com.jangingmall.backend.global.exception.ErrorCode;
import com.jangingmall.backend.global.exception.GlobalExceptionHandler;
import com.jangingmall.backend.payment.application.CartService;
import com.jangingmall.backend.payment.application.DeliveryService;
import com.jangingmall.backend.payment.application.PaymentProfileService;
import com.jangingmall.backend.payment.application.PaymentService;
import com.jangingmall.backend.payment.application.ReturnService;
import com.jangingmall.backend.payment.domain.DeliveryStatus;
import com.jangingmall.backend.payment.domain.OrderStatus;
import com.jangingmall.backend.payment.domain.PaymentMethod;
import com.jangingmall.backend.payment.domain.PaymentStatus;
import com.jangingmall.backend.payment.domain.ReturnStatus;
import com.jangingmall.backend.payment.domain.ReturnType;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(PaymentController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class PaymentControllerTest extends RestDocsControllerTest {

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
            .andExpect(jsonPath("$.data[0].isDefault").value(true))
            .andDo(MockMvcRestDocumentationWrapper.document(
                "payment-methods-list",
                resource(ResourceSnippetParameters.builder()
                    .tag("결제수단")
                    .summary("결제수단 목록 조회")
                    .description("인증된 회원의 등록된 결제수단 목록을 반환합니다.")
                    .responseFields(successEnvelopeFields(
                        fieldWithPath("data[]").type(JsonFieldType.ARRAY).description("결제수단 목록"),
                        fieldWithPath("data[].paymentMethodId").type(JsonFieldType.NUMBER).description("결제수단 ID"),
                        fieldWithPath("data[].type").type(JsonFieldType.STRING).description("결제수단 유형 (CARD 등)"),
                        fieldWithPath("data[].cardCompany").type(JsonFieldType.STRING).optional().description("카드사명"),
                        fieldWithPath("data[].cardNumberMasked").type(JsonFieldType.STRING).optional().description("마스킹된 카드번호"),
                        fieldWithPath("data[].isDefault").type(JsonFieldType.BOOLEAN).description("기본 결제수단 여부")
                    ))
                    .build()
                )
            ));

        when(paymentProfiles.methods(1L)).thenReturn(List.of());
        mockMvc.perform(get("/api/payments/methods").with(user()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    @DisplayName("PAY-P2-003/009/013 결제수단 API는 JWT 없으면 401이다")
    @WithAnonymousUser
    void protectsPaymentMethodEndpoints() throws Exception {
        mockMvc.perform(get("/api/payments/methods"))
            .andExpect(status().isUnauthorized())
            .andDo(documentError("payment-methods-list-unauthorized", "결제수단", "결제수단 목록 조회 — 인증 없음", "인증 없이 접근하면 401을 반환합니다."));
        mockMvc.perform(post("/api/payments/methods").contentType(APPLICATION_JSON).content(validCard()))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/payments/methods/10"))
            .andExpect(status().isUnauthorized());
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
            .andExpect(jsonPath("$.data.paymentMethodId").value(10))
            .andDo(MockMvcRestDocumentationWrapper.document(
                "payment-methods-register",
                resource(ResourceSnippetParameters.builder()
                    .tag("결제수단")
                    .summary("결제수단 등록")
                    .description("새 결제수단(카드)을 등록합니다.")
                    .requestFields(
                        fieldWithPath("type").type(JsonFieldType.STRING).description("결제수단 유형 (CARD)"),
                        fieldWithPath("cardNumber").type(JsonFieldType.STRING).description("카드번호 (13~25자리, 숫자·공백·하이픈)"),
                        fieldWithPath("expiry").type(JsonFieldType.STRING).description("유효기간 (MM/YY)"),
                        fieldWithPath("birthOrBusinessNo").type(JsonFieldType.STRING).description("생년월일 6자리 또는 사업자번호 10자리")
                    )
                    .responseFields(successEnvelopeFields(
                        fieldWithPath("data.paymentMethodId").type(JsonFieldType.NUMBER).description("등록된 결제수단 ID"),
                        fieldWithPath("data.type").type(JsonFieldType.STRING).description("결제수단 유형"),
                        fieldWithPath("data.cardCompany").type(JsonFieldType.STRING).optional().description("카드사명"),
                        fieldWithPath("data.cardNumberMasked").type(JsonFieldType.STRING).optional().description("마스킹된 카드번호"),
                        fieldWithPath("data.isDefault").type(JsonFieldType.BOOLEAN).description("기본 결제수단 여부")
                    ))
                    .build()
                )
            ));
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
            .andExpect(status().isOk())
            .andDo(MockMvcRestDocumentationWrapper.document(
                "payment-methods-delete",
                resource(ResourceSnippetParameters.builder()
                    .tag("결제수단")
                    .summary("결제수단 삭제")
                    .description("등록된 결제수단을 삭제합니다. 타인의 결제수단이거나 존재하지 않으면 404를 반환합니다.")
                    .responseFields(successEnvelopeFields(
                        fieldWithPath("data").type(JsonFieldType.NULL).optional().description("없음")
                    ))
                    .build()
                )
            ));
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
            .andExpect(jsonPath("$.data.refundAccountId").value(20))
            .andDo(MockMvcRestDocumentationWrapper.document(
                "payment-refund-account-register",
                resource(ResourceSnippetParameters.builder()
                    .tag("결제수단")
                    .summary("환불계좌 등록")
                    .description("환불 수령용 계좌를 등록합니다.")
                    .requestFields(
                        fieldWithPath("bankName").type(JsonFieldType.STRING).description("은행명 (한글·영문)"),
                        fieldWithPath("accountNumber").type(JsonFieldType.STRING).description("계좌번호 (숫자·하이픈 8~30자)"),
                        fieldWithPath("accountHolder").type(JsonFieldType.STRING).description("예금주명")
                    )
                    .responseFields(successEnvelopeFields(
                        fieldWithPath("data.refundAccountId").type(JsonFieldType.NUMBER).description("환불계좌 ID"),
                        fieldWithPath("data.bankName").type(JsonFieldType.STRING).description("은행명"),
                        fieldWithPath("data.accountNumberMasked").type(JsonFieldType.STRING).description("마스킹된 계좌번호"),
                        fieldWithPath("data.accountHolder").type(JsonFieldType.STRING).description("예금주명")
                    ))
                    .build()
                )
            ));
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
    @DisplayName("PAY-P2-017 환불계좌 API는 JWT 없으면 401이다")
    @WithAnonymousUser
    void protectsRefundAccountEndpoint() throws Exception {
        mockMvc.perform(post("/api/payments/refund-account").contentType(APPLICATION_JSON).content(validRefundAccount()))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("PAY-P2-018 환불계좌 서버 오류는 500이다")
    void mapsRefundAccountServerError() throws Exception {
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
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.sections").isEmpty())
            .andDo(MockMvcRestDocumentationWrapper.document(
                "cart-get",
                resource(ResourceSnippetParameters.builder()
                    .tag("장바구니")
                    .summary("장바구니 조회")
                    .description("인증 회원 또는 게스트(guestCartId 쿠키)의 장바구니를 반환합니다. 인증 없이도 접근 가능합니다.\n\n" +
                        "**정책**\n" +
                        "- 비회원(게스트) 장바구니 허용 — `guestCartId` 쿠키로 식별\n" +
                        "- 최대 50종, 초과 시 가장 오래된 항목을 찜으로 이동\n" +
                        "- 장바구니 진입 시 가격·재고·판매 상태 재조회\n" +
                        "- 배송비: 장인 단위 계산, 같은 장인 2점째부터 배송비 0\n" +
                        "- 주문제작 포함 시 전체 상품 함께 배송 (분리 배송 없음)")
                    .responseFields(successEnvelopeFields(
                        fieldWithPath("data.sections").type(JsonFieldType.ARRAY).description("아티산별 장바구니 섹션 목록"),
                        fieldWithPath("data.totalPrice").type(JsonFieldType.NUMBER).description("상품 합계 금액"),
                        fieldWithPath("data.totalShippingFee").type(JsonFieldType.NUMBER).description("배송비 합계"),
                        fieldWithPath("data.totalCount").type(JsonFieldType.NUMBER).description("총 상품 수")
                    ))
                    .build()
                )
            ));
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
            .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("guestCartId=guest-new")))
            .andDo(MockMvcRestDocumentationWrapper.document(
                "cart-add-item",
                resource(ResourceSnippetParameters.builder()
                    .tag("장바구니")
                    .summary("장바구니 상품 추가")
                    .description("장바구니에 상품을 추가합니다. 비로그인 시 `guestCartId` 쿠키를 Set-Cookie로 응답합니다.\n\n" +
                        "**정책**\n" +
                        "- 비회원(게스트) 허용 — 로그인 없이 장바구니 담기 가능\n" +
                        "- 장바구니 최대 50종; 초과 시 가장 오래된 항목을 찜으로 이동\n" +
                        "- 주문 생성(`POST /api/payments/orders`)부터 로그인 필수")
                    .requestFields(
                        fieldWithPath("productId").type(JsonFieldType.NUMBER).description("상품 ID"),
                        fieldWithPath("quantity").type(JsonFieldType.NUMBER).description("수량 (1 이상)"),
                        fieldWithPath("selectedOptions").type(JsonFieldType.ARRAY).optional().description("선택 옵션 목록"),
                        fieldWithPath("selectedOptions[].optionGroupId").type(JsonFieldType.NUMBER).optional().description("옵션 그룹 ID"),
                        fieldWithPath("selectedOptions[].choiceId").type(JsonFieldType.NUMBER).optional().description("선택지 ID"),
                        fieldWithPath("textInputs").type(JsonFieldType.ARRAY).optional().description("텍스트 입력 옵션 목록"),
                        fieldWithPath("textInputs[].optionGroupId").type(JsonFieldType.NUMBER).optional().description("옵션 그룹 ID"),
                        fieldWithPath("textInputs[].text").type(JsonFieldType.STRING).optional().description("입력 텍스트")
                    )
                    .responseFields(successEnvelopeFields(
                        fieldWithPath("data.sections").type(JsonFieldType.ARRAY).description("아티산별 장바구니 섹션 목록"),
                        fieldWithPath("data.totalPrice").type(JsonFieldType.NUMBER).description("상품 합계 금액"),
                        fieldWithPath("data.totalShippingFee").type(JsonFieldType.NUMBER).description("배송비 합계"),
                        fieldWithPath("data.totalCount").type(JsonFieldType.NUMBER).description("총 상품 수")
                    ))
                    .build()
                )
            ));
        mockMvc.perform(post("/api/payments/cart/items").contentType(APPLICATION_JSON)
                .content("{\"productId\":7,\"quantity\":0}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("PAY-P0-013 장바구니 수량 변경은 200을 반환한다")
    void changesCartItemQuantity() throws Exception {
        when(carts.changeQuantity(eq(1L), eq(null), eq(10L), eq(3))).thenReturn(
            new CartService.CartMutation(emptyCart(), null));
        mockMvc.perform(patch("/api/payments/cart/items/10").with(user()).contentType(APPLICATION_JSON)
                .content("{\"quantity\":3}"))
            .andExpect(status().isOk())
            .andDo(MockMvcRestDocumentationWrapper.document(
                "cart-change-quantity",
                resource(ResourceSnippetParameters.builder()
                    .tag("장바구니")
                    .summary("장바구니 수량 변경")
                    .description("장바구니 항목의 수량을 변경합니다.")
                    .requestFields(
                        fieldWithPath("quantity").type(JsonFieldType.NUMBER).description("변경할 수량 (1 이상)")
                    )
                    .responseFields(successEnvelopeFields(
                        fieldWithPath("data.sections").type(JsonFieldType.ARRAY).description("아티산별 장바구니 섹션 목록"),
                        fieldWithPath("data.totalPrice").type(JsonFieldType.NUMBER).description("상품 합계 금액"),
                        fieldWithPath("data.totalShippingFee").type(JsonFieldType.NUMBER).description("배송비 합계"),
                        fieldWithPath("data.totalCount").type(JsonFieldType.NUMBER).description("총 상품 수")
                    ))
                    .build()
                )
            ));
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
    @DisplayName("PAY-P0-018 장바구니 항목 단건 삭제는 200을 반환한다")
    void deletesCartItem() throws Exception {
        mockMvc.perform(delete("/api/payments/cart/items/10").with(user()))
            .andExpect(status().isOk())
            .andDo(MockMvcRestDocumentationWrapper.document(
                "cart-delete-item",
                resource(ResourceSnippetParameters.builder()
                    .tag("장바구니")
                    .summary("장바구니 항목 삭제")
                    .description("장바구니에서 특정 항목을 삭제합니다.")
                    .responseFields(successEnvelopeFields(
                        fieldWithPath("data").type(JsonFieldType.NULL).optional().description("없음")
                    ))
                    .build()
                )
            ));
    }

    @Test
    @DisplayName("PAY-P0-022 장바구니 전체 비우기는 200을 반환한다")
    void deletesAllCartItems() throws Exception {
        mockMvc.perform(delete("/api/payments/cart/items").with(user()))
            .andExpect(status().isOk())
            .andDo(MockMvcRestDocumentationWrapper.document(
                "cart-delete-all",
                resource(ResourceSnippetParameters.builder()
                    .tag("장바구니")
                    .summary("장바구니 전체 비우기")
                    .description("장바구니의 모든 항목을 삭제합니다.")
                    .responseFields(successEnvelopeFields(
                        fieldWithPath("data").type(JsonFieldType.NULL).optional().description("없음")
                    ))
                    .build()
                )
            ));
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
                org.hamcrest.Matchers.containsString("Max-Age=0"))))
            .andDo(MockMvcRestDocumentationWrapper.document(
                "cart-merge",
                resource(ResourceSnippetParameters.builder()
                    .tag("장바구니")
                    .summary("게스트 장바구니 병합")
                    .description("로그인 후 게스트 장바구니를 회원 장바구니에 병합합니다. 병합 성공 시 `guestCartId` 쿠키를 Max-Age=0으로 만료시킵니다.\n\n" +
                        "**정책**\n" +
                        "- 동일 상품+옵션 조합은 수량 합산\n" +
                        "- `guestCartId` 쿠키 없으면 400 반환")
                    .responseFields(successEnvelopeFields(
                        fieldWithPath("data.sections").type(JsonFieldType.ARRAY).description("아티산별 장바구니 섹션 목록"),
                        fieldWithPath("data.totalPrice").type(JsonFieldType.NUMBER).description("상품 합계 금액"),
                        fieldWithPath("data.totalShippingFee").type(JsonFieldType.NUMBER).description("배송비 합계"),
                        fieldWithPath("data.totalCount").type(JsonFieldType.NUMBER).description("총 상품 수")
                    ))
                    .build()
                )
            ));
    }

    @Test
    @DisplayName("PAY-P0-030 장바구니 병합은 JWT 없으면 401이다")
    @WithAnonymousUser
    void protectsCartMerge() throws Exception {
        mockMvc.perform(post("/api/payments/cart/merge"))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"))
            .andDo(documentError("cart-merge-unauthorized", "장바구니", "게스트 장바구니 병합 — 인증 없음", "인증 없이 접근하면 401을 반환합니다."));
    }

    @Test
    @DisplayName("PAY-P0-023 장바구니 옵션 변경은 200을 반환한다")
    void changesCartItemOptions() throws Exception {
        when(carts.changeOptions(eq(1L), eq(null), eq(10L), any())).thenReturn(
            new CartService.CartMutation(emptyCart(), null));
        mockMvc.perform(patch("/api/payments/cart/items/10/options").with(user()).contentType(APPLICATION_JSON)
                .content("{\"quantity\":2}"))
            .andExpect(status().isOk())
            .andDo(MockMvcRestDocumentationWrapper.document(
                "cart-change-options",
                resource(ResourceSnippetParameters.builder()
                    .tag("장바구니")
                    .summary("장바구니 옵션 변경")
                    .description("장바구니 항목의 수량 및 선택 옵션을 변경합니다.")
                    .requestFields(
                        fieldWithPath("quantity").type(JsonFieldType.NUMBER).optional().description("변경할 수량 (1 이상, 미입력 시 유지)"),
                        fieldWithPath("selectedOptions").type(JsonFieldType.ARRAY).optional().description("변경할 선택 옵션 목록"),
                        fieldWithPath("selectedOptions[].optionGroupId").type(JsonFieldType.NUMBER).optional().description("옵션 그룹 ID"),
                        fieldWithPath("selectedOptions[].choiceId").type(JsonFieldType.NUMBER).optional().description("선택지 ID"),
                        fieldWithPath("textInputs").type(JsonFieldType.ARRAY).optional().description("변경할 텍스트 입력 목록"),
                        fieldWithPath("textInputs[].optionGroupId").type(JsonFieldType.NUMBER).optional().description("옵션 그룹 ID"),
                        fieldWithPath("textInputs[].text").type(JsonFieldType.STRING).optional().description("입력 텍스트")
                    )
                    .responseFields(successEnvelopeFields(
                        fieldWithPath("data.sections").type(JsonFieldType.ARRAY).description("아티산별 장바구니 섹션 목록"),
                        fieldWithPath("data.totalPrice").type(JsonFieldType.NUMBER).description("상품 합계 금액"),
                        fieldWithPath("data.totalShippingFee").type(JsonFieldType.NUMBER).description("배송비 합계"),
                        fieldWithPath("data.totalCount").type(JsonFieldType.NUMBER).description("총 상품 수")
                    ))
                    .build()
                )
            ));
    }

    @Test
    @DisplayName("PAY-P0-031 정상 주문 생성은 201 Created다")
    void createsOrder() throws Exception {
        PaymentService.OrderData data = new PaymentService.OrderData(100L, "ORD-000001", 25_000L,
            OrderStatus.CREATED, Instant.now(), List.of());
        when(payments.createOrder(eq(1L), any(), eq("checkout-1"))).thenReturn(data);
        mockMvc.perform(post("/api/payments/orders").with(user()).header("Idempotency-Key", "checkout-1")
                .contentType(APPLICATION_JSON).content(validOrder()))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.data.orderId").value(100))
            .andDo(MockMvcRestDocumentationWrapper.document(
                "orders-create",
                resource(ResourceSnippetParameters.builder()
                    .tag("주문")
                    .summary("주문 생성")
                    .description("장바구니 항목으로 주문을 생성합니다. `Idempotency-Key` 헤더로 중복 생성을 방지합니다.\n\n" +
                        "**정책**\n" +
                        "- 주문번호 형식: `ORD{YYYYMMDD}{NNN}` (예: `ORD20260825001`)\n" +
                        "- 주문 상태: CREATED → PAID → SHIPPING → DELIVERED → CANCELED\n" +
                        "- 결제 직전 재고 재검증; 부족분은 행 단위로 고지\n" +
                        "- 주문제작 포함 시 전체 상품 함께 배송\n" +
                        "- 로그인 필수")
                    .requestFields(
                        fieldWithPath("cartItemIds").type(JsonFieldType.ARRAY).description("주문할 장바구니 항목 ID 목록"),
                        fieldWithPath("addressId").type(JsonFieldType.NUMBER).description("배송지 ID"),
                        fieldWithPath("deliveryRequest").type(JsonFieldType.STRING).optional().description("배송 요청사항 (최대 100자)"),
                        fieldWithPath("paymentMethod").type(JsonFieldType.STRING).description("결제수단 (CARD 등)")
                    )
                    .responseFields(successEnvelopeFields(
                        fieldWithPath("data.orderId").type(JsonFieldType.NUMBER).description("주문 ID"),
                        fieldWithPath("data.orderNumber").type(JsonFieldType.STRING).description("주문번호"),
                        fieldWithPath("data.totalAmount").type(JsonFieldType.NUMBER).description("결제 예정 금액"),
                        fieldWithPath("data.status").type(JsonFieldType.STRING).description("주문 상태"),
                        fieldWithPath("data.createdAt").type(JsonFieldType.STRING).description("주문 생성 일시"),
                        fieldWithPath("data.items").type(JsonFieldType.ARRAY).description("주문 항목 목록")
                    ))
                    .build()
                )
            ));
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
    @WithAnonymousUser
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
            .andExpect(status().isCreated())
            .andDo(MockMvcRestDocumentationWrapper.document(
                "payment-prepare",
                resource(ResourceSnippetParameters.builder()
                    .tag("결제")
                    .summary("결제 준비")
                    .description("토스페이먼츠 위젯 초기화에 필요한 `tossClientKey`와 `paymentId`를 반환합니다.\n\n" +
                        "**흐름**: FE는 이 값으로 위젯을 마운트 → 사용자가 결제수단 선택 → `POST /api/payments/confirm` 호출\n\n" +
                        "**정책**\n" +
                        "- 최초 준비: 201 Created\n" +
                        "- 기존 READY 상태 재사용: 200 OK\n" +
                        "- PG: 토스페이먼츠 (실시간 계좌이체·무통장·간편결제·카드)")
                    .requestFields(
                        fieldWithPath("orderId").type(JsonFieldType.NUMBER).description("주문 ID"),
                        fieldWithPath("amount").type(JsonFieldType.NUMBER).description("결제 금액"),
                        fieldWithPath("paymentMethod").type(JsonFieldType.STRING).optional().description("결제수단")
                    )
                    .responseFields(successEnvelopeFields(
                        fieldWithPath("data.paymentId").type(JsonFieldType.NUMBER).description("결제 ID"),
                        fieldWithPath("data.orderId").type(JsonFieldType.NUMBER).description("주문 ID"),
                        fieldWithPath("data.amount").type(JsonFieldType.NUMBER).description("결제 금액"),
                        fieldWithPath("data.tossClientKey").type(JsonFieldType.STRING).description("토스페이먼츠 클라이언트 키"),
                        fieldWithPath("data.created").type(JsonFieldType.BOOLEAN).description("신규 생성 여부")
                    ))
                    .build()
                )
            ));
        mockMvc.perform(post("/api/payments").with(user()).contentType(APPLICATION_JSON).content(body))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PAY-P0-043 결제 승인 성공은 200을 반환한다")
    void confirmsPayment() throws Exception {
        PaymentService.PaymentData data = new PaymentService.PaymentData(200L, 100L, "ORD-000001", 25_000L,
            PaymentMethod.CARD, PaymentStatus.DONE, Instant.now(), Instant.now());
        when(payments.confirm(eq(1L), any())).thenReturn(data);
        mockMvc.perform(post("/api/payments/confirm").with(user()).contentType(APPLICATION_JSON)
                .content("{\"paymentKey\":\"pay_key\",\"orderId\":\"ORD-000001\",\"amount\":25000}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("DONE"))
            .andDo(MockMvcRestDocumentationWrapper.document(
                "payment-confirm",
                resource(ResourceSnippetParameters.builder()
                    .tag("결제")
                    .summary("결제 승인")
                    .description("토스페이먼츠 결제 완료 후 FE가 전달받은 `paymentKey`, `orderId`, `amount`를 그대로 BE로 전달합니다.\n\n" +
                        "**정책**\n" +
                        "- BE가 토스페이먼츠 서버 측 승인 API를 호출하여 재검증 (클라이언트 응답만으로 확정 금지)\n" +
                        "- 중복 결제 방지: 주문번호 단위 멱등 처리\n" +
                        "- `orderId` 불일치 또는 금액 위변조 감지 시 422 반환")
                    .requestFields(
                        fieldWithPath("paymentKey").type(JsonFieldType.STRING).description("토스페이먼츠 결제 키 (최대 200자)"),
                        fieldWithPath("orderId").type(JsonFieldType.STRING).description("주문번호 (6~64자)"),
                        fieldWithPath("amount").type(JsonFieldType.NUMBER).description("결제 금액")
                    )
                    .responseFields(successEnvelopeFields(
                        fieldWithPath("data.paymentId").type(JsonFieldType.NUMBER).description("결제 ID"),
                        fieldWithPath("data.orderId").type(JsonFieldType.NUMBER).description("주문 ID"),
                        fieldWithPath("data.orderNumber").type(JsonFieldType.STRING).description("주문번호"),
                        fieldWithPath("data.amount").type(JsonFieldType.NUMBER).description("결제 금액"),
                        fieldWithPath("data.method").type(JsonFieldType.STRING).description("결제수단"),
                        fieldWithPath("data.status").type(JsonFieldType.STRING).description("결제 상태"),
                        fieldWithPath("data.approvedAt").type(JsonFieldType.STRING).optional().description("승인 일시"),
                        fieldWithPath("data.canceledAt").type(JsonFieldType.STRING).optional().description("취소 일시")
                    ))
                    .build()
                )
            ));
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
    @DisplayName("PAY-WEBHOOK 토스 웹훅은 200을 반환한다")
    void handlesTossWebhook() throws Exception {
        mockMvc.perform(post("/api/payments/webhooks/toss").contentType(APPLICATION_JSON)
                .content("{\"eventType\":\"PAYMENT_STATUS_CHANGED\",\"data\":{\"paymentKey\":\"pk\",\"orderId\":\"ORD-000001\",\"totalAmount\":25000,\"status\":\"DONE\"}}"))
            .andExpect(status().isOk())
            .andDo(MockMvcRestDocumentationWrapper.document(
                "payment-webhook-toss",
                resource(ResourceSnippetParameters.builder()
                    .tag("결제")
                    .summary("토스페이먼츠 웹훅 수신")
                    .description("토스페이먼츠에서 발송하는 결제 상태 변경 웹훅을 처리합니다.")
                    .requestFields(
                        fieldWithPath("eventType").type(JsonFieldType.STRING).description("이벤트 유형"),
                        fieldWithPath("data").type(JsonFieldType.OBJECT).description("이벤트 데이터"),
                        fieldWithPath("data.paymentKey").type(JsonFieldType.STRING).description("결제 키"),
                        fieldWithPath("data.orderId").type(JsonFieldType.STRING).description("주문번호"),
                        fieldWithPath("data.totalAmount").type(JsonFieldType.NUMBER).description("총 결제 금액"),
                        fieldWithPath("data.status").type(JsonFieldType.STRING).description("결제 상태")
                    )
                    .responseFields(successEnvelopeFields(
                        fieldWithPath("data").type(JsonFieldType.NULL).optional().description("없음")
                    ))
                    .build()
                )
            ));
    }

    @Test
    @DisplayName("PAY-P0-051~054 실패 처리 API는 정상 200·없는 대상 404·JWT 없음 401이다")
    void mapsPaymentFailureEndpoint() throws Exception {
        String body = "{\"orderId\":\"ORD-000001\",\"errorCode\":\"REJECTED\",\"errorMessage\":\"거절\"}";
        mockMvc.perform(post("/api/payments/fail").with(user()).contentType(APPLICATION_JSON).content(body))
            .andExpect(status().isOk())
            .andDo(MockMvcRestDocumentationWrapper.document(
                "payment-fail",
                resource(ResourceSnippetParameters.builder()
                    .tag("결제")
                    .summary("결제 실패 처리")
                    .description("결제 실패를 처리하고 주문 상태를 업데이트합니다.")
                    .requestFields(
                        fieldWithPath("orderId").type(JsonFieldType.STRING).description("주문번호 (6~64자)"),
                        fieldWithPath("errorCode").type(JsonFieldType.STRING).description("결제 실패 코드 (최대 100자)"),
                        fieldWithPath("errorMessage").type(JsonFieldType.STRING).description("실패 메시지 (최대 255자)")
                    )
                    .responseFields(successEnvelopeFields(
                        fieldWithPath("data").type(JsonFieldType.NULL).optional().description("없음")
                    ))
                    .build()
                )
            ));
        doThrow(new DomainException(ErrorCode.NOT_FOUND)).when(payments).fail(eq(1L), any());
        mockMvc.perform(post("/api/payments/fail").with(user()).contentType(APPLICATION_JSON).content(body))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PAY-P0-053 결제 실패 API는 JWT 없으면 401이다")
    @WithAnonymousUser
    void protectsPaymentFailEndpoint() throws Exception {
        mockMvc.perform(post("/api/payments/fail").contentType(APPLICATION_JSON)
                .content("{\"orderId\":\"ORD-000001\",\"errorCode\":\"REJECTED\",\"errorMessage\":\"거절\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("PAY-P0-055~061 취소 API는 200과 도메인 오류 상태를 전달한다")
    void mapsCancellationEndpoint() throws Exception {
        PaymentService.PaymentData data = new PaymentService.PaymentData(200L, 100L, "ORD-000001", 25_000L,
            PaymentMethod.CARD, PaymentStatus.CANCELED, Instant.now(), Instant.now());
        when(payments.cancel(1L, 200L, "취소")).thenReturn(data);
        mockMvc.perform(post("/api/payments/200/cancel").with(user()).contentType(APPLICATION_JSON)
                .content("{\"reason\":\"취소\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("CANCELED"))
            .andDo(MockMvcRestDocumentationWrapper.document(
                "payment-cancel",
                resource(ResourceSnippetParameters.builder()
                    .tag("결제")
                    .summary("결제 취소")
                    .description("완료된 결제를 취소합니다.\n\n" +
                        "**정책**\n" +
                        "- 주문제작 착수 후 취소 불가\n" +
                        "- 배송 시작 후 취소 불가\n" +
                        "- 부분 취소 시 배송비 재계산 반영")
                    .requestFields(
                        fieldWithPath("reason").type(JsonFieldType.STRING).description("취소 사유 (최대 200자)")
                    )
                    .responseFields(successEnvelopeFields(
                        fieldWithPath("data.paymentId").type(JsonFieldType.NUMBER).description("결제 ID"),
                        fieldWithPath("data.orderId").type(JsonFieldType.NUMBER).description("주문 ID"),
                        fieldWithPath("data.orderNumber").type(JsonFieldType.STRING).description("주문번호"),
                        fieldWithPath("data.amount").type(JsonFieldType.NUMBER).description("결제 금액"),
                        fieldWithPath("data.method").type(JsonFieldType.STRING).description("결제수단"),
                        fieldWithPath("data.status").type(JsonFieldType.STRING).description("결제 상태 (CANCELED)"),
                        fieldWithPath("data.approvedAt").type(JsonFieldType.STRING).optional().description("승인 일시"),
                        fieldWithPath("data.canceledAt").type(JsonFieldType.STRING).optional().description("취소 일시")
                    ))
                    .build()
                )
            ));
    }

    @Test
    @DisplayName("PAY-P0-060 취소 API는 JWT 없으면 401이다")
    @WithAnonymousUser
    void protectsPaymentCancelEndpoint() throws Exception {
        mockMvc.perform(post("/api/payments/200/cancel").contentType(APPLICATION_JSON)
                .content("{\"reason\":\"취소\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("PAY-P1-003~007 배송조회는 200·404·401 계약을 전달한다")
    void mapsDeliveryEndpoint() throws Exception {
        when(deliveries.get(1L, 100L)).thenReturn(new DeliveryService.DeliveryData(
            100L, "CJ대한통운", "1234567890", DeliveryStatus.IN_TRANSIT));
        mockMvc.perform(get("/api/payments/orders/100/delivery").with(user()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.trackingNumber").value("1234567890"))
            .andDo(MockMvcRestDocumentationWrapper.document(
                "delivery-get",
                resource(ResourceSnippetParameters.builder()
                    .tag("배송")
                    .summary("배송 조회")
                    .description("주문의 배송 추적 정보를 조회합니다.\n\n" +
                        "**정책**\n" +
                        "- 배송 완료 7일 후 구매 자동 확정 (스케줄러)\n" +
                        "- 택배사 API 미연동 시 운송장 번호 복사만 제공 (P1)")
                    .responseFields(successEnvelopeFields(
                        fieldWithPath("data.orderId").type(JsonFieldType.NUMBER).description("주문 ID"),
                        fieldWithPath("data.carrier").type(JsonFieldType.STRING).description("택배사명"),
                        fieldWithPath("data.trackingNumber").type(JsonFieldType.STRING).description("운송장 번호"),
                        fieldWithPath("data.status").type(JsonFieldType.STRING).description("배송 상태")
                    ))
                    .build()
                )
            ));
        when(deliveries.get(1L, 101L)).thenThrow(new DomainException(ErrorCode.NOT_FOUND));
        mockMvc.perform(get("/api/payments/orders/101/delivery").with(user())).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PAY-P3-001 반품 신청은 201 Created를 반환한다")
    void requestsReturn() throws Exception {
        when(returns.request(eq(1L), any())).thenReturn(
            new ReturnService.ReturnData(300L, 100L, ReturnType.RETURN, ReturnStatus.REQUESTED, Instant.now()));
        mockMvc.perform(post("/api/payments/returns").with(user()).contentType(APPLICATION_JSON)
                .content("{\"orderId\":100,\"type\":\"RETURN\",\"orderItemIds\":[1],\"reason\":\"DEFECTIVE\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.returnId").value(300))
            .andDo(MockMvcRestDocumentationWrapper.document(
                "returns-request",
                resource(ResourceSnippetParameters.builder()
                    .tag("반품/교환")
                    .summary("반품·교환 신청")
                    .description("주문 항목에 대해 반품 또는 교환을 신청합니다.\n\n" +
                        "**정책**\n" +
                        "- 신청 기한: 수령 후 7일 이내\n" +
                        "- 신청 시 주문 상태가 `RETURN_REQUESTED`로 전환\n" +
                        "- 주문당 반품·교환 신청 한 건만 허용\n" +
                        "- 클레임 상태 흐름: 접수 → 검토 중 → 승인/반려 → 회수 → 완료\n" +
                        "- 귀책 판정 3종: 판매자 귀책 / 구매자 귀책 / 협의")
                    .requestFields(
                        fieldWithPath("orderId").type(JsonFieldType.NUMBER).description("주문 ID"),
                        fieldWithPath("type").type(JsonFieldType.STRING).description("유형 (RETURN: 반품, EXCHANGE: 교환)"),
                        fieldWithPath("orderItemIds").type(JsonFieldType.ARRAY).description("대상 주문 항목 ID 목록 (1개 이상)"),
                        fieldWithPath("reason").type(JsonFieldType.STRING).description("반품 사유 코드"),
                        fieldWithPath("description").type(JsonFieldType.STRING).optional().description("상세 사유 (최대 500자)"),
                        fieldWithPath("imageIds").type(JsonFieldType.ARRAY).optional().description("첨부 이미지 ID 목록 (최대 5개)"),
                        fieldWithPath("returnAddressId").type(JsonFieldType.NUMBER).optional().description("반품 수거 주소 ID")
                    )
                    .responseFields(successEnvelopeFields(
                        fieldWithPath("data.returnId").type(JsonFieldType.NUMBER).description("반품 ID"),
                        fieldWithPath("data.orderId").type(JsonFieldType.NUMBER).description("주문 ID"),
                        fieldWithPath("data.type").type(JsonFieldType.STRING).description("반품·교환 유형"),
                        fieldWithPath("data.status").type(JsonFieldType.STRING).description("처리 상태"),
                        fieldWithPath("data.requestedAt").type(JsonFieldType.STRING).description("신청 일시")
                    ))
                    .build()
                )
            ));
    }

    private RequestPostProcessor user() {
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
