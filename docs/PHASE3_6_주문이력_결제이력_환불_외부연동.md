# 산출물 6 — 주문 이력·결제 이력·환불 API + 외부 통합

---

## MemberQueryController.java (주문·리뷰 이력)
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

@GetMapping("/reviews")
@PreAuthorize("hasRole('USER')")
public ApiResponse<Page<Map<String, Object>>> reviews(
    @AuthenticationPrincipal Long memberId,
    @PageableDefault(size = 20) Pageable pageable
) {
    return ApiResponse.ok(queries.reviews(memberId, pageable, false));
}

@GetMapping("/reviews/writable")
@PreAuthorize("hasRole('USER')")
public ApiResponse<Page<Map<String, Object>>> writable(
    @AuthenticationPrincipal Long memberId,
    @PageableDefault(size = 20) Pageable pageable
) {
    return ApiResponse.ok(queries.reviews(memberId, pageable, true));
}
```

---

## MemberReadRepositoryImpl.java (주문·리뷰 이력 JPQL)
`src/main/java/com/jangingmall/backend/member/infrastructure/MemberReadRepositoryImpl.java`

```java
@Override
public Page<Map<String, Object>> orders(Long memberId, Pageable pageable, String status) {
    String filter = orderStatus(status);  // ALL, CREATED, PAID, DELIVERED, CANCELED, ...
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

@Override
public Optional<Map<String, Object>> order(Long memberId, Long orderId) {
    return entityManager.createQuery(
            "SELECT o FROM MemberOrderView o WHERE o.id=:id AND o.memberId=:memberId", MemberOrderView.class)
        .setParameter("id", orderId).setParameter("memberId", memberId)
        .getResultStream().findFirst().map(this::orderDetail);
}

private Page<Map<String, Object>> writtenReviews(Long memberId, Pageable pageable) {
    String from = " FROM ProductReview r, Member m WHERE r.writerId=m.id AND r.writerId=:memberId";
    List<Object[]> rows = entityManager.createQuery(
            "SELECT r,m" + from + " ORDER BY r.id DESC", Object[].class)
        .setParameter("memberId", memberId)
        .setFirstResult((int) pageable.getOffset()).setMaxResults(pageable.getPageSize())
        .getResultList();
    long total = entityManager.createQuery(
            "SELECT count(r) FROM ProductReview r WHERE r.writerId=:memberId", Long.class)
        .setParameter("memberId", memberId).getSingleResult();
    List<Map<String, Object>> items = rows.stream().map(row -> {
        ProductReview review = (ProductReview) row[0];
        Member writer = (Member) row[1];
        return Map.of(
            "reviewId", review.getId(),
            "productId", review.getProductId(),
            "rating", review.getRating(),
            "content", review.getContent(),
            "writerNickname", Optional.ofNullable(writer.getNickname()).orElse(writer.getName()),
            "createdAt", review.getCreatedAt()
        );
    }).toList();
    return new PageImpl<>(items, pageable, total);
}

private Page<Map<String, Object>> writableReviews(Long memberId, Pageable pageable) {
    String from = " FROM MemberOrderItemView i, MemberOrderView o, Product p"
        + " WHERE i.orderId=o.id AND i.productId=p.id AND o.memberId=:memberId"
        + " AND o.status='DELIVERED'"
        + " AND NOT EXISTS (SELECT r.id FROM ProductReview r WHERE r.orderItemId=i.id)";
    List<Object[]> rows = entityManager.createQuery(
            "SELECT i,p" + from + " ORDER BY i.id DESC", Object[].class)
        .setParameter("memberId", memberId)
        .setFirstResult((int) pageable.getOffset()).setMaxResults(pageable.getPageSize())
        .getResultList();
    long total = entityManager.createQuery("SELECT count(i)" + from, Long.class)
        .setParameter("memberId", memberId).getSingleResult();
    List<Map<String, Object>> items = rows.stream().map(row -> {
        MemberOrderItemView item = (MemberOrderItemView) row[0];
        Product product = (Product) row[1];
        return Map.of(
            "orderItemId", item.getId(),
            "productId", item.getProductId(),
            "productName", item.getProductName(),
            "thumbnail", thumbnail(product.getThumbnailUrl())
        );
    }).toList();
    return new PageImpl<>(items, pageable, total);
}
```

---

## 외부 통합 구현체

### TossPaymentsGateway.java
`src/main/java/com/jangingmall/backend/payment/infrastructure/TossPaymentsGateway.java`

> Toss Payments 승인·취소·조회 REST API 실 연동 (`PaymentGateway` 구현체)

### SweetTrackerDeliveryTrackingGateway.java
`src/main/java/com/jangingmall/backend/payment/infrastructure/SweetTrackerDeliveryTrackingGateway.java`

> SweetTracker 배송 추적 실 연동 (`DeliveryTrackingGateway` 구현체)

### RestAiContentClient.java
`src/main/java/com/jangingmall/backend/content/infrastructure/RestAiContentClient.java`

> AI 서버 → 콘텐츠 생성·동기화 HTTP 연동 (`AiContentClient` 구현체)

### RestAiChatClient.java
`src/main/java/com/jangingmall/backend/chatbot/infrastructure/RestAiChatClient.java`

> AI 서버 → 채팅 HTTP 연동 (`AiChatClient` 구현체)

### DevAuthController.java (로컬 Mock)
`src/main/java/com/jangingmall/backend/global/dev/DevAuthController.java`

> `local` 프로파일 전용 인증 Mock (`/dev/**`). 프로덕션 빌드 미포함.
