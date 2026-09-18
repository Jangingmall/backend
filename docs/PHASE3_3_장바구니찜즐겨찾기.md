# 산출물 3 — 장바구니·찜·즐겨찾기 API

---

## PaymentController.java (장바구니 관련)
`src/main/java/com/jangingmall/backend/payment/presentation/PaymentController.java`

```java
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private static final String GUEST_CART_COOKIE = "guestCartId";

    @GetMapping("/cart")
    public ApiResponse<CartService.CartData> cart(
        @AuthenticationPrincipal Long memberId,
        @CookieValue(value = GUEST_CART_COOKIE, required = false) String guestCartId
    ) {
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
    public ApiResponse<Void> deleteCartItem(
        @AuthenticationPrincipal Long memberId,
        @CookieValue(value = GUEST_CART_COOKIE, required = false) String guestCartId,
        @PathVariable Long cartItemId
    ) {
        carts.delete(memberId, guestCartId, cartItemId);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/cart/items")
    public ApiResponse<Void> deleteAllCartItems(
        @AuthenticationPrincipal Long memberId,
        @CookieValue(value = GUEST_CART_COOKIE, required = false) String guestCartId
    ) {
        carts.deleteAll(memberId, guestCartId);
        return ApiResponse.ok(null);
    }

    @PostMapping("/cart/merge")
    public ResponseEntity<ApiResponse<CartService.CartData>> mergeCart(
        @AuthenticationPrincipal Long memberId,
        @CookieValue(value = GUEST_CART_COOKIE, required = false) String guestCartId
    ) {
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
}
```

---

## CartService.java
`src/main/java/com/jangingmall/backend/payment/application/CartService.java`

```java
@Service
@RequiredArgsConstructor
public class CartService implements CheckoutCartReader {

    private final CartRepository carts;
    private final CartItemRepository cartItems;
    private final CheckoutCatalog catalog;
    private final ObjectMapper json;

    @Transactional
    public CartMutation add(Long memberId, String guestCartId, CartCommand command) {
        List<CheckoutCatalog.OptionSelection> options = safe(command.selectedOptions());
        List<CheckoutCatalog.TextInput> textInputs = safe(command.textInputs());
        String optionKey = optionKey(options);
        String textKey = textKey(textInputs);
        Cart existingCart = find(memberId, guestCartId);
        CartItem matching = existingCart == null ? null : cartItems.findByCartIdOrderByIdAsc(existingCart.getId()).stream()
            .filter(item -> item.getProductId().equals(command.productId())
                && item.getSelectedOptions().equals(optionKey) && item.getTextInputs().equals(textKey))
            .findFirst().orElse(null);
        int finalQuantity = matching == null ? command.quantity() : Math.addExact(matching.getQuantity(), command.quantity());
        catalog.quote(command.productId(), finalQuantity, options, textInputs);
        Cart cart = existingCart == null ? findOrCreate(memberId, guestCartId) : existingCart;
        if (matching == null) {
            matching = cartItems.save(new CartItem(cart.getId(), command.productId(), command.quantity(), optionKey, textKey));
        } else {
            matching.addQuantity(command.quantity());
        }
        cart.touch();
        return new CartMutation(cartData(cartItems.findByCartIdOrderByIdAsc(cart.getId())), cart.getGuestCartId());
    }

    @Transactional
    public CartMutation merge(Long memberId, String guestCartId) {
        if (guestCartId == null || guestCartId.isBlank()) {
            throw new DomainException(ErrorCode.INVALID_INPUT);
        }
        Cart guest = carts.findByGuestCartId(guestCartId).orElse(null);
        Cart member = findOrCreate(memberId, null);
        if (guest == null || guest.getId().equals(member.getId())) {
            return new CartMutation(cartData(cartItems.findByCartIdOrderByIdAsc(member.getId())), null);
        }
        List<CartItem> memberItems = cartItems.findByCartIdOrderByIdAsc(member.getId());
        for (CartItem guestItem : cartItems.findByCartIdOrderByIdAsc(guest.getId())) {
            CartItem matching = memberItems.stream().filter(item ->
                item.getProductId().equals(guestItem.getProductId())
                && item.getSelectedOptions().equals(guestItem.getSelectedOptions())
                && item.getTextInputs().equals(guestItem.getTextInputs()))
                .findFirst().orElse(null);
            int finalQuantity = matching == null ? guestItem.getQuantity()
                : Math.addExact(matching.getQuantity(), guestItem.getQuantity());
            catalog.quote(guestItem.getProductId(), finalQuantity, parseOptions(guestItem.getSelectedOptions()),
                parseTextInputs(guestItem.getTextInputs()));
            if (matching == null) {
                guestItem.moveTo(member.getId());
                memberItems.add(guestItem);
            } else {
                matching.addQuantity(guestItem.getQuantity());
                cartItems.delete(guestItem);
            }
        }
        carts.delete(guest);
        member.touch();
        return new CartMutation(cartData(memberItems), null);
    }
}
```

---

## MemberQueryController.java (찜 목록·단건 조회)
`src/main/java/com/jangingmall/backend/member/presentation/MemberQueryController.java`

```java
@RestController
@RequestMapping("/api/member/me")
@RequiredArgsConstructor
public class MemberQueryController {

    @GetMapping("/wishes")
    @PreAuthorize("hasRole('USER')")
    public ApiResponse<Page<Map<String, Object>>> wishes(
        @AuthenticationPrincipal Long memberId,
        @PageableDefault(size = 20) Pageable pageable
    ) {
        return ApiResponse.ok(queries.wishes(memberId, pageable));
    }

    @GetMapping("/wishes/{productId}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Void> isWished(
        @AuthenticationPrincipal Long memberId,
        @PathVariable Long productId
    ) {
        return queries.isWished(memberId, productId)
            ? ResponseEntity.noContent().build()
            : ResponseEntity.notFound().build();
    }
}
```

---

## MemberActivityController.java (최근 본 상품·구독)
`src/main/java/com/jangingmall/backend/member/presentation/MemberActivityController.java`

```java
@RestController
@RequestMapping("/api/member")
@RequiredArgsConstructor
public class MemberActivityController {

    @GetMapping("/recent-views")
    public Page<Map<String, Object>> recent(
        @AuthenticationPrincipal Long memberId,
        @PageableDefault(size = 20) Pageable pageable
    ) {
        return activities.recentViews(memberId, pageable);
    }

    @PostMapping("/recent-views")
    public ApiResponse<Void> record(@AuthenticationPrincipal Long memberId, @Valid @RequestBody RecentView request) {
        activities.recordView(memberId, request.productId());
        return ApiResponse.ok(null);
    }

    @PostMapping("/recent-views/merge")
    public ApiResponse<Void> merge(@AuthenticationPrincipal Long memberId, @Valid @RequestBody RecentViews request) {
        activities.mergeViews(memberId, request.productIds());
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/recent-views")
    public ApiResponse<Void> clear(@AuthenticationPrincipal Long memberId) {
        activities.clearViews(memberId);
        return ApiResponse.ok(null);
    }

    @PostMapping("/artisans/{artisanId}/subscribe")
    public ApiResponse<Void> subscribe(@AuthenticationPrincipal Long memberId, @PathVariable Long artisanId) {
        activities.subscribe(memberId, artisanId);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/artisans/{artisanId}/subscribe")
    public ApiResponse<Void> unsubscribe(@AuthenticationPrincipal Long memberId, @PathVariable Long artisanId) {
        activities.unsubscribe(memberId, artisanId);
        return ApiResponse.ok(null);
    }

    @GetMapping("/artisans/subscriptions")
    public Page<Map<String, Object>> subscriptions(
        @AuthenticationPrincipal Long memberId,
        @PageableDefault(size = 20) Pageable pageable
    ) {
        return activities.subscriptions(memberId, pageable);
    }
}
```

---

## MemberReadRepositoryImpl.java (찜 목록 — offset 페이지네이션)
`src/main/java/com/jangingmall/backend/member/infrastructure/MemberReadRepositoryImpl.java`

```java
@Override
public Page<Map<String, Object>> wishes(Long memberId, Pageable pageable) {
    String joins = " FROM Wishlist w, Product p, ArtisanProfile a, Member m"
        + " WHERE w.productId=p.id AND p.artisanId=a.id AND m.id=a.id"
        + " AND w.memberId=:memberId"
        + " AND p.status IN :statuses AND m.status=:active AND a.certificationStatus=:approved";
    List<Object[]> rows = entityManager.createQuery(
            "SELECT w,p,a" + joins + " ORDER BY w.id DESC", Object[].class)
        .setParameter("memberId", memberId)
        .setParameter("statuses", VISIBLE_PRODUCTS).setParameter("active", MemberStatus.ACTIVE)
        .setParameter("approved", APPROVED)
        .setFirstResult((int) pageable.getOffset()).setMaxResults(pageable.getPageSize())
        .getResultList();
    long total = entityManager.createQuery("SELECT count(w)" + joins, Long.class)
        .setParameter("memberId", memberId)
        .setParameter("statuses", VISIBLE_PRODUCTS).setParameter("active", MemberStatus.ACTIVE)
        .setParameter("approved", APPROVED).getSingleResult();
    List<Map<String, Object>> items = rows.stream()
        .map(row -> product((Product) row[1], (ArtisanProfile) row[2])).toList();
    return new PageImpl<>(items, pageable, total);
}
```
