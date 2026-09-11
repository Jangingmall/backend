package com.jangingmall.backend.payment.presentation;

import com.jangingmall.backend.global.common.response.ApiResponse;
import com.jangingmall.backend.payment.application.CartService;
import com.jangingmall.backend.payment.application.CheckoutCatalog;
import com.jangingmall.backend.payment.application.DeliveryService;
import com.jangingmall.backend.payment.application.PaymentService;
import com.jangingmall.backend.payment.application.PaymentProfileService;
import com.jangingmall.backend.payment.application.ReturnService;
import com.jangingmall.backend.payment.domain.PaymentMethod;
import com.jangingmall.backend.payment.domain.ReturnReason;
import com.jangingmall.backend.payment.domain.ReturnType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private static final String GUEST_CART_COOKIE = "guestCartId";
    private final CartService carts;
    private final PaymentService payments;
    private final PaymentProfileService paymentProfiles;
    private final DeliveryService deliveries;
    private final ReturnService returns;

    @GetMapping("/methods")
    public ApiResponse<List<PaymentProfileService.PaymentMethodData>> paymentMethods(
        @AuthenticationPrincipal Long memberId
    ) {
        return ApiResponse.ok(paymentProfiles.methods(memberId));
    }

    @PostMapping("/methods")
    public ResponseEntity<ApiResponse<PaymentProfileService.PaymentMethodData>> registerPaymentMethod(
        @AuthenticationPrincipal Long memberId,
        @Valid @RequestBody RegisterPaymentMethodRequest request
    ) {
        return ResponseEntity.status(201).body(ApiResponse.created(
            paymentProfiles.registerMethod(memberId, request.toCommand())));
    }

    @DeleteMapping("/methods/{paymentMethodId}")
    public ApiResponse<Void> deletePaymentMethod(@AuthenticationPrincipal Long memberId,
                                                  @PathVariable Long paymentMethodId) {
        paymentProfiles.deleteMethod(memberId, paymentMethodId);
        return ApiResponse.ok(null);
    }

    @PostMapping("/refund-account")
    public ResponseEntity<ApiResponse<PaymentProfileService.RefundAccountData>> registerRefundAccount(
        @AuthenticationPrincipal Long memberId,
        @Valid @RequestBody RegisterRefundAccountRequest request
    ) {
        return ResponseEntity.status(201).body(ApiResponse.created(
            paymentProfiles.registerRefundAccount(memberId, request.toCommand())));
    }

    @GetMapping("/cart")
    public ApiResponse<CartService.CartData> cart(@AuthenticationPrincipal Long memberId,
                                                  @CookieValue(value = GUEST_CART_COOKIE, required = false) String guestCartId) {
        return ApiResponse.ok(carts.get(memberId, guestCartId));
    }

    @PostMapping("/cart/items")
    public ResponseEntity<ApiResponse<CartService.CartData>> addCartItem(
        @AuthenticationPrincipal Long memberId,
        @CookieValue(value = GUEST_CART_COOKIE, required = false) String guestCartId,
        @Valid @RequestBody CartItemRequest request
    ) {
        CartService.CartMutation result = carts.add(memberId, guestCartId, request.toCommand());
        return cartResponse(result, true);
    }

    @PatchMapping("/cart/items/{cartItemId}")
    public ResponseEntity<ApiResponse<CartService.CartData>> changeQuantity(
        @AuthenticationPrincipal Long memberId,
        @CookieValue(value = GUEST_CART_COOKIE, required = false) String guestCartId,
        @PathVariable Long cartItemId,
        @Valid @RequestBody QuantityRequest request
    ) {
        return cartResponse(carts.changeQuantity(memberId, guestCartId, cartItemId, request.quantity()), false);
    }

    @DeleteMapping("/cart/items/{cartItemId}")
    public ApiResponse<Void> deleteCartItem(@AuthenticationPrincipal Long memberId,
                                            @CookieValue(value = GUEST_CART_COOKIE, required = false) String guestCartId,
                                            @PathVariable Long cartItemId) {
        carts.delete(memberId, guestCartId, cartItemId);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/cart/items")
    public ApiResponse<Void> deleteAllCartItems(@AuthenticationPrincipal Long memberId,
                                                @CookieValue(value = GUEST_CART_COOKIE, required = false) String guestCartId) {
        carts.deleteAll(memberId, guestCartId);
        return ApiResponse.ok(null);
    }

    @PostMapping("/cart/merge")
    public ResponseEntity<ApiResponse<CartService.CartData>> mergeCart(@AuthenticationPrincipal Long memberId,
                                                                        @CookieValue(value = GUEST_CART_COOKIE, required = false) String guestCartId) {
        CartService.CartMutation result = carts.merge(memberId, guestCartId);
        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, expiredGuestCartCookie().toString())
            .body(ApiResponse.ok(result.cart()));
    }

    @PatchMapping("/cart/items/{cartItemId}/options")
    public ResponseEntity<ApiResponse<CartService.CartData>> changeOptions(
        @AuthenticationPrincipal Long memberId,
        @CookieValue(value = GUEST_CART_COOKIE, required = false) String guestCartId,
        @PathVariable Long cartItemId,
        @Valid @RequestBody CartOptionsRequest request
    ) {
        return cartResponse(carts.changeOptions(memberId, guestCartId, cartItemId, request.toCommand()), false);
    }

    @PostMapping("/orders")
    public ResponseEntity<ApiResponse<PaymentService.OrderData>> createOrder(
        @AuthenticationPrincipal Long memberId,
        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
        @Valid @RequestBody CreateOrderRequest request
    ) {
        return ResponseEntity.status(201).body(ApiResponse.created(payments.createOrder(memberId, request.toCommand(), idempotencyKey)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PaymentService.PreparedPayment>> prepare(
        @AuthenticationPrincipal Long memberId,
        @Valid @RequestBody PreparePaymentRequest request
    ) {
        PaymentService.PreparedPayment result = payments.prepare(memberId, request.toCommand());
        return result.created()
            ? ResponseEntity.status(201).body(ApiResponse.created(result))
            : ResponseEntity.ok(ApiResponse.ok(result));
    }

    @PostMapping("/confirm")
    public ApiResponse<PaymentService.PaymentData> confirm(@AuthenticationPrincipal Long memberId,
                                                            @Valid @RequestBody ConfirmPaymentRequest request) {
        return ApiResponse.ok(payments.confirm(memberId, request.toCommand()));
    }

    @PostMapping("/webhooks/toss")
    public ApiResponse<Void> tossWebhook(@Valid @RequestBody TossWebhookRequest request) {
        payments.handleWebhook(request.toCommand());
        return ApiResponse.ok(null);
    }

    @PostMapping("/fail")
    public ApiResponse<Void> fail(@AuthenticationPrincipal Long memberId, @Valid @RequestBody FailPaymentRequest request) {
        payments.fail(memberId, request.toCommand());
        return ApiResponse.ok(null);
    }

    @PostMapping("/{paymentId}/cancel")
    public ApiResponse<PaymentService.PaymentData> cancel(@AuthenticationPrincipal Long memberId, @PathVariable Long paymentId,
                                                           @Valid @RequestBody CancelPaymentRequest request) {
        return ApiResponse.ok(payments.cancel(memberId, paymentId, request.reason()));
    }

    @GetMapping("/orders/{orderId}/delivery")
    public ApiResponse<DeliveryService.DeliveryData> delivery(@AuthenticationPrincipal Long memberId, @PathVariable Long orderId) {
        return ApiResponse.ok(deliveries.get(memberId, orderId));
    }

    @PostMapping("/returns")
    public ResponseEntity<ApiResponse<ReturnService.ReturnData>> requestReturn(
        @AuthenticationPrincipal Long memberId,
        @Valid @RequestBody ReturnRequest request
    ) {
        return ResponseEntity.status(201).body(ApiResponse.created(returns.request(memberId, request.toCommand())));
    }

    private ResponseEntity<ApiResponse<CartService.CartData>> cartResponse(CartService.CartMutation result, boolean created) {
        ResponseEntity.BodyBuilder response = created ? ResponseEntity.status(201) : ResponseEntity.ok();
        if (result.guestCartId() != null) {
            response.header(HttpHeaders.SET_COOKIE, guestCartCookie(result.guestCartId()).toString());
        }
        return response.body(created ? ApiResponse.created(result.cart()) : ApiResponse.ok(result.cart()));
    }

    private ResponseCookie guestCartCookie(String guestCartId) {
        return ResponseCookie.from(GUEST_CART_COOKIE, guestCartId)
            .httpOnly(true).sameSite("Lax").path("/").maxAge(60L * 60 * 24 * 30).build();
    }

    private ResponseCookie expiredGuestCartCookie() {
        return ResponseCookie.from(GUEST_CART_COOKIE, "")
            .httpOnly(true).sameSite("Lax").path("/").maxAge(0).build();
    }

    public record CartItemRequest(@NotNull Long productId, @Positive int quantity,
                                  List<@Valid OptionSelectionRequest> selectedOptions,
                                  List<@Valid TextInputRequest> textInputs) {
        CartService.CartCommand toCommand() {
            return new CartService.CartCommand(productId, quantity, optionSelections(selectedOptions), PaymentController.textInputs(textInputs));
        }
    }

    public record QuantityRequest(@Positive int quantity) {}

    public record RegisterPaymentMethodRequest(
        @NotNull PaymentMethod type,
        @NotBlank @Pattern(regexp = "[0-9 -]{13,25}") String cardNumber,
        @NotBlank @Pattern(regexp = "(0[1-9]|1[0-2])/?[0-9]{2}") String expiry,
        @NotBlank @Pattern(regexp = "([0-9]{6}|[0-9]{10})") String birthOrBusinessNo
    ) {
        PaymentProfileService.RegisterPaymentMethod toCommand() {
            return new PaymentProfileService.RegisterPaymentMethod(type, cardNumber, expiry, birthOrBusinessNo);
        }
    }

    public record RegisterRefundAccountRequest(
        @NotBlank @Size(max = 50) @Pattern(regexp = "[가-힣A-Za-z ]+") String bankName,
        @NotBlank @Pattern(regexp = "[0-9-]{8,30}") String accountNumber,
        @NotBlank @Size(max = 50) @Pattern(regexp = "[가-힣A-Za-z ]+") String accountHolder
    ) {
        PaymentProfileService.RegisterRefundAccount toCommand() {
            return new PaymentProfileService.RegisterRefundAccount(bankName, accountNumber, accountHolder);
        }
    }

    public record CartOptionsRequest(@Positive Integer quantity, List<@Valid OptionSelectionRequest> selectedOptions,
                                     List<@Valid TextInputRequest> textInputs) {
        CartService.CartOptionsCommand toCommand() {
            return new CartService.CartOptionsCommand(quantity, optionSelectionsOrNull(selectedOptions), PaymentController.textInputsOrNull(textInputs));
        }
    }

    public record OptionSelectionRequest(@NotNull Long optionGroupId, @NotNull Long choiceId) {}
    public record TextInputRequest(@NotNull Long optionGroupId, @NotBlank @Size(max = 500) String text) {}

    public record CreateOrderRequest(@NotNull List<@NotNull Long> cartItemIds, @NotNull Long addressId,
                                     @Size(max = 100) String deliveryRequest, @NotNull PaymentMethod paymentMethod) {
        PaymentService.CreateOrder toCommand() {
            return new PaymentService.CreateOrder(cartItemIds, addressId, deliveryRequest, paymentMethod);
        }
    }

    public record PreparePaymentRequest(@NotNull Long orderId, @Positive long amount, PaymentMethod paymentMethod) {
        PaymentService.Prepare toCommand() { return new PaymentService.Prepare(orderId, amount, paymentMethod); }
    }

    public record ConfirmPaymentRequest(@NotBlank @Size(max = 200) String paymentKey,
                                        @NotBlank @Size(min = 6, max = 64) String orderId,
                                        @Positive long amount) {
        PaymentService.Confirm toCommand() { return new PaymentService.Confirm(paymentKey, orderId, amount); }
    }

    public record FailPaymentRequest(@NotBlank @Size(min = 6, max = 64) String orderId,
                                     @NotBlank @Size(max = 100) String errorCode,
                                     @NotBlank @Size(max = 255) String errorMessage) {
        PaymentService.Fail toCommand() { return new PaymentService.Fail(orderId, errorCode, errorMessage); }
    }

    public record CancelPaymentRequest(@NotBlank @Size(max = 200) String reason) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TossWebhookRequest(@NotBlank String eventType, @NotNull @Valid TossPaymentData data) {
        PaymentService.Webhook toCommand() {
            return new PaymentService.Webhook(eventType, data.paymentKey(), data.orderId(), data.totalAmount(), data.status());
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TossPaymentData(@NotBlank @Size(max = 200) String paymentKey,
                                  @NotBlank @Size(min = 6, max = 64) String orderId,
                                  @Positive long totalAmount,
                                  @NotBlank @Size(max = 30) String status) {}

    public record ReturnRequest(@NotNull Long orderId, @NotNull ReturnType type,
                                @NotEmpty List<@NotNull Long> orderItemIds, @NotNull ReturnReason reason,
                                @JsonAlias("reasonDetail") @Size(max = 500) String description,
                                @JsonAlias("returnPhotoKeys") @Size(max = 5) List<@NotBlank @Size(max = 30) String> imageIds,
                                Long returnAddressId) {
        ReturnService.RequestReturn toCommand() {
            return new ReturnService.RequestReturn(orderId, type, orderItemIds, reason, description, imageIds, returnAddressId);
        }
    }

    private static List<CheckoutCatalog.OptionSelection> optionSelections(List<OptionSelectionRequest> selections) {
        return selections == null ? List.of() : selections.stream()
            .map(selection -> new CheckoutCatalog.OptionSelection(selection.optionGroupId(), selection.choiceId())).toList();
    }

    private static List<CheckoutCatalog.OptionSelection> optionSelectionsOrNull(List<OptionSelectionRequest> selections) {
        return selections == null ? null : optionSelections(selections);
    }

    private static List<CheckoutCatalog.TextInput> textInputs(List<TextInputRequest> inputs) {
        return inputs == null ? List.of() : inputs.stream()
            .map(input -> new CheckoutCatalog.TextInput(input.optionGroupId(), input.text())).toList();
    }

    private static List<CheckoutCatalog.TextInput> textInputsOrNull(List<TextInputRequest> inputs) {
        return inputs == null ? null : textInputs(inputs);
    }
}
