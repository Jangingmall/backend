# 산출물 4 — 주문 API + 트랜잭션·동시성 제어 (Idempotency Key·락)

---

## PaymentController.java (주문 생성)
`src/main/java/com/jangingmall/backend/payment/presentation/PaymentController.java`

```java
@PostMapping("/orders")
public ResponseEntity<ApiResponse<PaymentService.OrderData>> createOrder(
    @AuthenticationPrincipal Long memberId,
    @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
    @Valid @RequestBody CreateOrderRequest request
) {
    return ResponseEntity.status(201).body(
        ApiResponse.created(payments.createOrder(memberId, request.toCommand(), idempotencyKey))
    );
}

public record CreateOrderRequest(
    @NotNull List<@NotNull Long> cartItemIds,
    @NotNull Long addressId,
    @Size(max = 100) String deliveryRequest,
    @NotNull PaymentMethod paymentMethod
) {
    PaymentService.CreateOrder toCommand() {
        return new PaymentService.CreateOrder(cartItemIds, addressId, deliveryRequest, paymentMethod);
    }
}
```

---

## PaymentService.java (createOrder — Idempotency Key + 재고 락)
`src/main/java/com/jangingmall/backend/payment/application/PaymentService.java`

```java
@Transactional
public OrderData createOrder(Long memberId, CreateOrder command, String idempotencyKey) {
    memberAccess.requireRole(memberId, MemberRole.USER);
    String normalizedKey = normalizeIdempotencyKey(idempotencyKey);
    catalog.lockOrderCreation(memberId, normalizedKey);       // SELECT FOR UPDATE
    if (normalizedKey != null) {
        PurchaseOrder existing = orders.findByMemberIdAndClientRequestKey(memberId, normalizedKey).orElse(null);
        if (existing != null) {
            return OrderData.from(existing);                  // 중복 요청 → 기존 주문 반환
        }
    }
    if (command.paymentMethod() == null) {
        throw new BusinessRuleViolationException("결제수단을 선택해야 합니다.");
    }
    PurchaseOrder.ShippingAddress address = shippingAddresses.findOwned(memberId, command.addressId());
    List<CheckoutCartReader.CartLine> cartLines = checkoutCart.selectedItems(memberId, command.cartItemIds());
    List<QuotedLine> quotedLines = cartLines.stream().map(this::quote).toList();
    List<PurchaseOrder.OrderLine> lines = quotedLines.stream().map(this::toOrderLine).toList();
    long shippingAmount = shippingAmount(quotedLines);
    catalog.reserve(cartLines.stream().map(this::inventoryLine).toList()); // 재고 차감
    PurchaseOrder order = new PurchaseOrder(nextOrderNumber(), memberId, address, command.deliveryRequest(),
        command.paymentMethod(), normalizedKey, lines, shippingAmount);
    return OrderData.from(orders.save(order));
}

private String normalizeIdempotencyKey(String idempotencyKey) {
    if (idempotencyKey == null || idempotencyKey.isBlank()) return null;
    String normalized = idempotencyKey.trim();
    if (normalized.length() > 100) {
        throw new BusinessRuleViolationException("Idempotency-Key는 100자 이하여야 합니다.");
    }
    return normalized;
}
```

---

## JdbcCheckoutCatalog.java (분산 락 구현)
`src/main/java/com/jangingmall/backend/payment/infrastructure/JdbcCheckoutCatalog.java`

> `CheckoutCatalog.lockOrderCreation()` 구현체 — DB 레벨 SELECT FOR UPDATE

```java
@Override
public void lockOrderCreation(Long memberId, String idempotencyKey) {
    // memberId 기준으로 행을 잠궈 동일 회원의 동시 주문 생성을 방지
    jdbcTemplate.queryForObject(
        "SELECT id FROM members WHERE id = ? FOR UPDATE",
        Long.class, memberId
    );
}

@Override
public void reserve(List<InventoryLine> lines) {
    for (InventoryLine line : lines) {
        int updated = jdbcTemplate.update(
            "UPDATE products SET stock = stock - ? WHERE id = ? AND stock >= ?",
            line.quantity(), line.productId(), line.quantity()
        );
        if (updated == 0) {
            throw new BusinessRuleViolationException("재고가 부족합니다: productId=" + line.productId());
        }
    }
}
```

---

## OrderExpirationScheduler.java (미결제 주문 자동 만료)
`src/main/java/com/jangingmall/backend/payment/application/OrderExpirationScheduler.java`

```java
@Component
@RequiredArgsConstructor
public class OrderExpirationScheduler {

    private final PaymentService paymentService;

    @Scheduled(fixedDelay = 60_000)
    public void expire() {
        Instant cutoff = Instant.now().minus(Duration.ofMinutes(30));
        int processed = paymentService.expireCreatedOrders(cutoff);
        if (processed > 0) {
            log.info("만료 주문 처리 완료: {}건", processed);
        }
    }
}
```

---

## MemberQueryController.java (주문 이력 조회)
`src/main/java/com/jangingmall/backend/member/presentation/MemberQueryController.java`

```java
@GetMapping("/orders")
@PreAuthorize("hasRole('USER')")
public ApiResponse<Page<Map<String, Object>>> orders(
    @AuthenticationPrincipal Long memberId,
    @PageableDefault(size = 20) Pageable pageable,
    @RequestParam(defaultValue = "ALL") String status
) {
    return ApiResponse.ok(queries.orders(memberId, pageable, status));
}

@GetMapping("/orders/{orderId}")
@PreAuthorize("hasRole('USER')")
public ApiResponse<Map<String, Object>> order(
    @AuthenticationPrincipal Long memberId,
    @PathVariable Long orderId
) {
    return ApiResponse.ok(queries.order(memberId, orderId));
}
```

---

## MemberReadRepositoryImpl.java (주문 이력 — offset 페이지네이션)
`src/main/java/com/jangingmall/backend/member/infrastructure/MemberReadRepositoryImpl.java`

```java
@Override
public Page<Map<String, Object>> orders(Long memberId, Pageable pageable, String status) {
    String filter = orderStatus(status);
    String filtered = "ALL".equals(filter) ? "" : " AND o.status=:status";
    var query = entityManager.createQuery(
            "SELECT o FROM MemberOrderView o WHERE o.memberId=:memberId"
                + filtered + " ORDER BY o.id DESC", MemberOrderView.class)
        .setParameter("memberId", memberId)
        .setFirstResult((int) pageable.getOffset()).setMaxResults(pageable.getPageSize());
    var count = entityManager.createQuery(
            "SELECT count(o) FROM MemberOrderView o WHERE o.memberId=:memberId" + filtered, Long.class)
        .setParameter("memberId", memberId);
    if (!"ALL".equals(filter)) {
        query.setParameter("status", filter);
        count.setParameter("status", filter);
    }
    List<Map<String, Object>> items = query.getResultList().stream().map(this::orderSummary).toList();
    return new PageImpl<>(items, pageable, count.getSingleResult());
}
```
