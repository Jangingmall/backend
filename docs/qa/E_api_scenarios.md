# E. API Scenario / Business Flow

> INFERRED_SCENARIO: OpenAPI에 비즈니스 흐름 명시 없음. 엔드포인트 관계 추론.

---

## Scenario 1: 회원 가입 → 로그인 → 토큰 갱신

```
SCN-001
INFERRED_SCENARIO

Step 1: POST /api/member/signup          (member-signup)
        body: {email, password, name}
        expected: 201 Created
        capture: -

Step 2: POST /api/member/login           (member-login)
        body: {email, password}
        expected: 200 OK
        capture: {{accessToken}}, {{refreshToken(cookie)}}

Step 3: GET  /api/member/me              (member-get-me)
        header: Authorization: Bearer {{accessToken}}
        expected: 200 OK

Step 4: POST /api/member/token/refresh   (member-token-refresh)
        cookie: refreshToken={{refreshToken}}
        expected: 200 OK
        capture: {{newAccessToken}}

CONFIG_REQUIRED: 테스트 계정 이메일/패스워드
```

---

## Scenario 2: 카카오 OAuth 소셜 로그인

```
SCN-002
INFERRED_SCENARIO

Step 1: GET  /api/member/oauth2/kakao    (oauth-kakao-redirect)
        expected: 302 redirect to kakao

Step 2: POST /api/member/oauth2/exchange (oauth-exchange)
        body: {code, state}
        expected: 200 OK
        capture: {{accessToken}}

Step 3: POST /api/member/oauth2/complete-profile (oauth-complete-profile)
        body: {name, phone, ...}
        expected: 200 OK (프로필 미완성 계정인 경우)

BACKEND_INFO_REQUIRED: 카카오 sandbox 계정, code 발급 방법
```

---

## Scenario 3: 상품 등록 → 조회 → 수정 → 삭제 (ARTISAN)

```
SCN-003
INFERRED_SCENARIO

Pre-condition: ARTISAN 역할 계정 로그인 → {{artisanToken}}

Step 1: POST /api/products               (product-create)
        header: Authorization: Bearer {{artisanToken}}
        body: {title, price, stock, ...}
        expected: 201 Created
        capture: {{productId}}

Step 2: GET  /api/products/{{productId}} (product-detail)
        expected: 200 OK
        verify: response.id == {{productId}}

Step 3: PATCH /api/products/{{productId}} (product-update)
        body: {price: newPrice}
        expected: 200 OK

Step 4: PATCH /api/products/{{productId}}/status (product-change-status)
        body: {status: "ON_SALE"}
        expected: 200 OK
        SPEC_GAP: status enum 값 OpenAPI에 미정의

Step 5: DELETE /api/products/{{productId}} (product-delete)
        expected: 204 No Content

Step 6: GET /api/products/{{productId}}  (product-detail)
        expected: 404 NOT_FOUND

CLEANUP: Step 5가 Cleanup
```

---

## Scenario 4: 장바구니 → 주문 → 결제 (CORE FLOW)

```
SCN-004
INFERRED_SCENARIO

Pre-condition: USER 계정 로그인, 상품 존재

Step 1: POST /api/payments/cart/items    (cart-add-item)
        body: {productId, quantity, options}
        expected: 200 OK
        capture: {{cartItemId}}

Step 2: GET  /api/payments/cart          (cart-get)
        expected: 200 OK
        verify: items 배열에 {{cartItemId}} 포함

Step 3: POST /api/payments/orders        (orders-create)
        body: {cartItemIds, addressId, ...}
        expected: 201 Created
        capture: {{orderId}}

Step 4: POST /api/payments               (payment-prepare)
        body: {orderId, amount, ...}
        expected: 200 OK
        capture: {{paymentKey}}, {{tossOrderId}}

Step 5: POST /api/payments/confirm       (payment-confirm)
        body: {paymentKey, orderId: {{tossOrderId}}, amount}
        expected: 200 OK
        BACKEND_INFO_REQUIRED: Toss sandbox paymentKey 발급 방법

Step 6: GET  /api/member/me/orders/{{orderId}} (member-order-detail)
        expected: 200 OK
        verify: status == "PAID"
        SPEC_GAP: 결제 후 주문 상태 OpenAPI 미정의

TRANSACTION_RULE_REQUIRED: 결제 실패 시 주문 상태 rollback 정책
CLEANUP_GAP: 테스트 주문 삭제 API 없음
```

---

## Scenario 5: 반품 요청

```
SCN-005
INFERRED_SCENARIO

Pre-condition: 결제 완료 주문 {{orderId}} 존재, 배송완료 상태

Step 1: POST /api/payments/returns       (returns-request)
        body: {orderId, reason, ...}
        expected: 201 Created
        capture: {{returnId}}

SPEC_GAP: 반품 가능 조건 (배송완료 7일 이내 등) OpenAPI 미정의
BACKEND_INFO_REQUIRED: 반품 상태 전이 흐름
```

---

## Scenario 6: AI 콘텐츠 생성 (ARTISAN)

```
SCN-006
INFERRED_SCENARIO

Pre-condition: ARTISAN 계정, {{productId}} 존재

Step 1: POST /api/content/{{productId}}/interview   (interview-create)
Step 2: POST /api/content/{{productId}}/generations (generation-request)
        capture: {{generationId}}
Step 3: GET  /api/content/{{productId}}/generations/{{generationId}} (generation-poll-)
        polling until status != PROCESSING
        SPEC_GAP: generation status enum 미완전 (operationId: generation-poll- trailing hyphen)
Step 4: POST /api/content/{{productId}}/contents/{{contentId}}/approve (content-approve)

BACKEND_INFO_REQUIRED: AI 콜백 /internal/generations/{id}/completion mock 방법
```

---

## Scenario 7: Smoke Test Flow

```
SCN-007 (SMOKE)

Step 1: GET  /healthz                             → 200
Step 2: POST /api/member/login (테스트 계정)       → 200 + token
Step 3: GET  /api/member/me                       → 200
Step 4: GET  /api/products                        → 200
Step 5: GET  /api/products/categories             → 200
Step 6: GET  /api/payments/cart                   → 200
Step 7: POST /api/member/logout                   → 200

CONFIG_REQUIRED: 테스트 계정 자격증명
```
