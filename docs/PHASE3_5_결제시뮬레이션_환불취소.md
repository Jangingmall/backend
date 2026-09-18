# 산출물 5 — 결제 시뮬레이션 API + 환불·취소 처리

---

## PaymentController.java (결제 관련 엔드포인트)
`src/main/java/com/jangingmall/backend/payment/presentation/PaymentController.java`

```java
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
public ApiResponse<PaymentService.PaymentData> confirm(
    @AuthenticationPrincipal Long memberId,
    @Valid @RequestBody ConfirmPaymentRequest request
) {
    return ApiResponse.ok(payments.confirm(memberId, request.toCommand()));
}

@PostMapping("/fail")
public ApiResponse<Void> fail(
    @AuthenticationPrincipal Long memberId,
    @Valid @RequestBody FailPaymentRequest request
) {
    payments.fail(memberId, request.toCommand());
    return ApiResponse.ok(null);
}

@PostMapping("/{paymentId}/cancel")
public ApiResponse<PaymentService.PaymentData> cancel(
    @AuthenticationPrincipal Long memberId,
    @PathVariable Long paymentId,
    @Valid @RequestBody CancelPaymentRequest request
) {
    return ApiResponse.ok(payments.cancel(memberId, paymentId, request.reason()));
}

@PostMapping("/webhooks/toss")
public ApiResponse<Void> tossWebhook(@Valid @RequestBody TossWebhookRequest request) {
    payments.handleWebhook(request.toCommand());
    return ApiResponse.ok(null);
}

@PostMapping("/returns")
public ResponseEntity<ApiResponse<ReturnService.ReturnData>> requestReturn(
    @AuthenticationPrincipal Long memberId,
    @Valid @RequestBody ReturnRequest request
) {
    return ResponseEntity.status(201).body(ApiResponse.created(returns.request(memberId, request.toCommand())));
}

public record ConfirmPaymentRequest(
    @NotBlank @Size(max = 200) String paymentKey,
    @NotBlank @Size(min = 6, max = 64) String orderId,
    @Positive long amount
) {
    PaymentService.Confirm toCommand() { return new PaymentService.Confirm(paymentKey, orderId, amount); }
}

public record TossWebhookRequest(@NotBlank String eventType, @NotNull @Valid TossPaymentData data) {
    PaymentService.Webhook toCommand() {
        return new PaymentService.Webhook(eventType, data.paymentKey(), data.orderId(), data.totalAmount(), data.status());
    }
}

public record ReturnRequest(
    @NotNull Long orderId,
    @NotNull ReturnType type,
    @NotEmpty List<@NotNull Long> orderItemIds,
    @NotNull ReturnReason reason,
    @Size(max = 500) String description,
    @Size(max = 5) List<@NotBlank @Size(max = 30) String> imageIds,
    Long returnAddressId
) {
    ReturnService.RequestReturn toCommand() {
        return new ReturnService.RequestReturn(orderId, type, orderItemIds, reason, description, imageIds, returnAddressId);
    }
}
```

---

## PaymentService.java (confirm — 금액 검증 + Toss 승인)
`src/main/java/com/jangingmall/backend/payment/application/PaymentService.java`

```java
@Transactional(noRollbackFor = PaymentProviderRejectedException.class)
public PaymentData confirm(Long memberId, Confirm command) {
    memberAccess.requireRole(memberId, MemberRole.USER);
    PurchaseOrder order = orders.findByOrderNumberForUpdate(command.orderNumber())
        .orElseThrow(() -> new BusinessRuleViolationException("요청 orderId가 주문과 일치하지 않습니다."));
    if (!order.getMemberId().equals(memberId)) {
        throw new DomainException(ErrorCode.NOT_FOUND);
    }
    if (order.getTotalAmount() != command.amount()) {
        throw new DomainException(ErrorCode.PAYMENT_AMOUNT_MISMATCH);  // 422
    }
    Payment payment = payments.findByOrderIdForUpdate(order.getId())
        .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
    if (payment.isSameConfirmation(command.paymentKey()) && order.getStatus() == OrderStatus.PAID) {
        return PaymentData.from(payment, order);                         // 멱등성: 이미 처리된 요청
    }
    if (payment.getAmount() != command.amount()
        || payment.getStatus() != PaymentStatus.READY || order.getStatus() != OrderStatus.CREATED) {
        throw new BusinessRuleViolationException("결제 승인 가능한 상태가 아닙니다.");
    }
    payments.findByPaymentKey(command.paymentKey())
        .filter(found -> !found.getId().equals(payment.getId()))
        .ifPresent(found -> { throw new ConcurrentUpdateException("이미 처리된 paymentKey입니다."); });
    try {
        paymentGateway.confirm(command.paymentKey(), order.getOrderNumber(), command.amount());
    } catch (PaymentProviderRejectedException exception) {
        payment.fail(exception.providerCode(), exception.getMessage());
        order.markPaymentFailed();
        catalog.release(inventoryLines(order));
        throw exception;
    }
    completePayment(payment, order, command.paymentKey());
    return PaymentData.from(payment, order);
}

@Transactional
public PaymentData cancel(Long memberId, Long paymentId, String reason) {
    memberAccess.active(memberId);
    Payment candidate = payments.findById(paymentId)
        .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
    PurchaseOrder order = orders.findByIdForUpdate(candidate.getOrderId())
        .filter(o -> o.getMemberId().equals(memberId))
        .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
    Payment payment = payments.findByIdForUpdate(paymentId)
        .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
    if (payment.getStatus() == PaymentStatus.CANCELED || order.getStatus() == OrderStatus.CANCELED) {
        throw new ConcurrentUpdateException("이미 취소된 결제입니다.");
    }
    if (payment.getStatus() != PaymentStatus.DONE || order.getStatus() != OrderStatus.PAID || payment.getPaymentKey() == null) {
        throw new BusinessRuleViolationException("결제 완료 건만 취소할 수 있습니다.");
    }
    paymentGateway.cancel(payment.getPaymentKey(), reason);
    payment.cancel();
    order.cancel();
    List<CheckoutCatalog.InventoryLine> inventory = inventoryLines(order);
    catalog.release(inventory);
    catalog.changeSalesCount(inventory, -1);
    return PaymentData.from(payment, order);
}

@Transactional
public void handleWebhook(Webhook command) {
    if (!"PAYMENT_STATUS_CHANGED".equals(command.eventType())) return;
    PaymentGateway.PaymentSnapshot verified = paymentGateway.find(command.paymentKey());
    if (!command.paymentKey().equals(verified.paymentKey())
        || !command.orderNumber().equals(verified.orderNumber())
        || command.totalAmount() != verified.totalAmount()
        || !command.status().equals(verified.status())) {
        throw new ForbiddenException("검증되지 않은 결제 웹훅입니다.");
    }
    PurchaseOrder order = orders.findByOrderNumberForUpdate(verified.orderNumber())
        .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
    Payment payment = payments.findByOrderIdForUpdate(order.getId())
        .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
    switch (verified.status()) {
        case "DONE"     -> { /* ... */ completePayment(payment, order, verified.paymentKey()); }
        case "CANCELED" -> cancelFromWebhook(payment, order);
        case "ABORTED", "EXPIRED" -> failFromWebhook(payment, order, verified.status());
    }
}
```

---

## ErrorCode.java (PAYMENT_AMOUNT_MISMATCH)
`src/main/java/com/jangingmall/backend/global/exception/ErrorCode.java`

```java
PAYMENT_AMOUNT_MISMATCH(HttpStatus.UNPROCESSABLE_ENTITY, "결제 금액이 주문 금액과 일치하지 않습니다"),
```

---

## TossPaymentsGateway.java (외부 연동)
`src/main/java/com/jangingmall/backend/payment/infrastructure/TossPaymentsGateway.java`

> Toss Payments REST API 실 연동 구현체 (`PaymentGateway` 인터페이스)

```java
@Component
@RequiredArgsConstructor
public class TossPaymentsGateway implements PaymentGateway {

    private static final String CONFIRM_URL = "https://api.tosspayments.com/v1/payments/confirm";
    private static final String CANCEL_URL   = "https://api.tosspayments.com/v1/payments/{paymentKey}/cancel";

    private final RestClient restClient;
    private final PaymentProperties properties;

    @Override
    public void confirm(String paymentKey, String orderNumber, long amount) {
        try {
            restClient.post()
                .uri(CONFIRM_URL)
                .header(HttpHeaders.AUTHORIZATION, basicAuth())
                .body(Map.of("paymentKey", paymentKey, "orderId", orderNumber, "amount", amount))
                .retrieve()
                .toBodilessEntity();
        } catch (HttpClientErrorException exception) {
            throw new PaymentProviderRejectedException(parseCode(exception), exception.getMessage());
        }
    }

    @Override
    public void cancel(String paymentKey, String reason) {
        restClient.post()
            .uri(CANCEL_URL, paymentKey)
            .header(HttpHeaders.AUTHORIZATION, basicAuth())
            .body(Map.of("cancelReason", reason))
            .retrieve()
            .toBodilessEntity();
    }
}
```
