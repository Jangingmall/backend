# PHASE 2-1 — BE × FE API 협업 계약서

---

### 1. 문서 정보

| 버전 | 일자 | 변경 내용 | 변경 사유 | 영향 범위 | 작성자 |
| --- | --- | --- | --- | --- | --- |
| v0.1 | 2026-09-01 | 초안 작성 | 최초 작성 | - | BE / FE |

---

### 2. 개요

| 항목 | 내용 |
| --- | --- |
| 협업 직군 | BE, FE |
| 목적 | FE와 BE 간 API 계약을 사전에 정의하여 병렬 개발 및 연동 오류 방지 |
| 적용 범위 | '미담' 전체 API |

---

### 3. API 공통 규칙

| No. | 정의 항목 | 합의 내용 |
| --- | --- | --- |
| 3-1 | API 명세 관리 도구 | Notion, REST Docs |
| 3-2 | Base URL | `https://{domain}/api` |
| 3-3 | API 버전 관리 | 버전 prefix 생략 — `/api/{domain}` 으로 시작 |
| 3-4 | HTTP Method 규칙 | RESTful API (GET/POST/PATCH/DELETE) |
| 3-5 | Content-Type | `application/json` |
| 3-6 | 날짜/시간 포맷 | ISO 8601 (`2026-09-01T00:00:00Z`), UTF-8 인코딩 |
| 3-7 | HTTP Status Code | 2xx (성공), 3xx (리다이렉트), 4xx (클라이언트 오류), 5xx (서버 오류) |
| 3-8 | 공통 성공 응답 포맷 | `{ "success": true, "status": 200, "data": { ... } }` |
| 3-9 | 공통 에러 응답 포맷 | `{ "success": false, "status": 4xx, "errorCode": "CODE" }` |
| 3-10 | 페이지네이션 | Cursor 기반 (`cursor`, `limit` 쿼리 파라미터) |
| 3-11 | 정렬/필터 파라미터 | 쿼리 파라미터 (`sort`, `category`, `minPrice` 등) |
| 3-12 | 인증 정보 전달 방식 | JWT Bearer Token — `Authorization: Bearer {accessToken}` |

#### 3-8. 공통 성공 응답 포맷

```json
{
  "success": true,
  "status": 200,
  "data": { }
}
```

#### 3-9. 공통 에러 응답 포맷

```json
{
  "success": false,
  "status": 400,
  "errorCode": "INVALID_INPUT"
}
```

#### 주요 에러 코드

| errorCode | HTTP Status | 설명 |
| --- | --- | --- |
| `UNAUTHORIZED` | 401 | 인증 실패 (토큰 없음/만료) |
| `FORBIDDEN` | 403 | 권한 없음 |
| `NOT_FOUND` | 404 | 리소스 없음 |
| `CONFLICT` | 409 | 중복 또는 동시성 충돌 |
| `MISMATCH` | 400 | 값 불일치 |
| `INVALID_INPUT` | 400 | 요청 값 유효성 오류 |
| `BUSINESS_RULE_VIOLATION` | 422 | 비즈니스 규칙 위반 |
| `CONCURRENT_UPDATE` | 409 | 동시 업데이트 충돌 |

---

### 4. API 목록

| No. | 기능 | Method | Endpoint | 인증 | 우선순위 |
| --- | --- | --- | --- | --- | --- |
| 4-1 | 상품 목록 조회/검색/필터 | `GET` | `/api/products` | Public | P0 |
| 4-2 | 상품 상세 조회 | `GET` | `/api/products/{productId}` | Public | P0 |
| 4-3 | 상품 검색 (키워드) | `GET` | `/api/products?keyword={keyword}` | Public | P0 |
| 4-4 | 판매자 상품 등록 | `POST` | `/api/products` | ARTISAN | P0 |
| 4-5 | 판매자 상품 수정 | `PATCH` | `/api/products/{productId}` | ARTISAN | P0 |
| 4-6 | 주문 생성 | `POST` | `/api/payments/orders` | USER | P0 |
| 4-7 | 주문 목록 조회 | `GET` | `/api/member/me/orders` | USER | P1 |
| 4-8 | 주문 상세 조회 | `GET` | `/api/member/me/orders/{orderId}` | USER | P1 |
| 4-9 | AI 추천 챗봇 메시지 전송 | `POST` | `/api/chatbot/sessions/{sessionId}/messages` | Public | P2 |
| 4-10 | AI 상세페이지 생성 요청 | `POST` | `/api/content/products/{productId}/generations` | ARTISAN | P0 |
| 4-11 | 장바구니 담기 | `POST` | `/api/payments/cart/items` | Public(게스트 허용) | P0 |
| 4-12 | 장바구니 병합 (로그인 시) | `POST` | `/api/payments/cart/merge` | Authenticated | P0 |
| 4-13 | 결제 준비 | `POST` | `/api/payments` | USER | P0 |
| 4-14 | 결제 승인 | `POST` | `/api/payments/confirm` | USER | P0 |
| 4-15 | 배송 조회 | `GET` | `/api/payments/orders/{orderId}/delivery` | Authenticated | P1 |
| 4-16 | 반품 신청 | `POST` | `/api/payments/returns` | USER | P1 |
| 4-17 | 상품 찜 등록 | `POST` | `/api/products/{productId}/wish` | USER | P1 |
| 4-18 | 상품 찜 취소 | `DELETE` | `/api/products/{productId}/wish` | USER | P1 |
| 4-19 | 찜 목록 조회 | `GET` | `/api/member/me/wishes` | USER | P1 |
| 4-20 | 챗봇 세션 생성 | `POST` | `/api/chatbot/sessions` | Public | P2 |

---

### 5. Request / Response 계약

#### 5-1. 상품 목록 조회/검색/필터 `GET /api/products`

**Request**

```json
{
  "method": "GET",
  "url": "/api/products",
  "headers": {
    "Content-Type": "application/json"
  },
  "queryParams": {
    "cursor": "string · 선택",
    "limit": "string · 선택",
    "artisanId": "string · 선택",
    "category": "string (enum: POTTERY 등) · 선택",
    "material": "string · 선택",
    "giftTheme": "string (enum: HOUSEWARMING | WEDDING | PARENTS | PROMOTION | BIRTHDAY_60TH | BOSS | FRIEND | CORPORATE | COUPLE) · 선택",
    "color": "string (enum: WHITE | BLACK | GRAY | RED | BLUE | GREEN | BROWN) · 선택",
    "sort": "string (enum: POPULAR | NEWEST | WISHLIST_COUNT | SALES_COUNT | PRICE_ASC | PRICE_DESC) · 선택",
    "minPrice": "string · 선택",
    "maxPrice": "string · 선택",
    "isLimited": "string · 선택",
    "isCustomOrder": "string · 선택",
    "hasGiftWrap": "string · 선택",
    "excludeSoldOut": "string · 선택 (기본 true)",
    "keyword": "string · 선택"
  }
}
```

**Response** `200 OK`

```json
{
  "success": true,
  "status": 200,
  "data": {
    "items": [
      {
        "productId": "number",
        "name": "string",
        "price": "number",
        "thumbnail": [{ "url": "string", "width": "number", "height": "number", "format": "string" }],
        "status": "string (enum: ON_SALE | SOLD_OUT | HIDDEN | DRAFT)",
        "category": "string",
        "color": "string",
        "giftTheme": "string (enum: HOUSEWARMING | WEDDING | PARENTS | PROMOTION | BIRTHDAY_60TH | BOSS | FRIEND | CORPORATE | COUPLE)",
        "rating": "number | null",
        "isLimited": "boolean",
        "isCustomOrder": "boolean",
        "isSingleItem": "boolean",
        "isNew": "boolean",
        "hasGiftWrap": "boolean",
        "hasOptions": "boolean",
        "purposeTags": ["string"],
        "artisanId": "number",
        "artisanName": "string"
      }
    ],
    "nextCursor": "string",
    "hasNext": "boolean",
    "totalCount": "number"
  }
}
```

---

#### 5-2. 상품 상세 조회 `GET /api/products/{productId}`

**Request**

```json
{
  "method": "GET",
  "url": "/api/products/{productId}",
  "headers": {
    "Content-Type": "application/json"
  }
}
```

**Response** `200 OK`

```json
{
  "success": true,
  "status": 200,
  "data": {
    "productId": "number",
    "name": "string",
    "price": "number",
    "thumbnail": [{ "url": "string", "width": "number", "height": "number", "format": "string" }],
    "status": "string (enum: ON_SALE | SOLD_OUT | HIDDEN | DRAFT)",
    "category": "string",
    "color": "string",
    "giftTheme": "string (enum: HOUSEWARMING | WEDDING | PARENTS | PROMOTION | BIRTHDAY_60TH | BOSS | FRIEND | CORPORATE | COUPLE)",
    "rating": "number | null",
    "isLimited": "boolean",
    "isCustomOrder": "boolean",
    "isSingleItem": "boolean",
    "isNew": "boolean",
    "hasGiftWrap": "boolean",
    "hasOptions": "boolean",
    "purposeTags": ["string"],
    "artisanId": "number",
    "artisanName": "string",
    "modelName": "string",
    "size": "string",
    "material": "string",
    "components": ["string"],
    "manufacturer": "string",
    "countryOfOrigin": "string",
    "asManagerContact": "string",
    "warrantyPolicy": "string",
    "description": "string",
    "stock": "number",
    "stockAvailable": "boolean",
    "productionPeriodDays": "number",
    "engravingAvailable": "boolean",
    "shippingFee": "number",
    "freeShippingThreshold": "number | null",
    "detailPageBlocks": [
      {
        "order": "number",
        "tag": "string (enum: h2 | p | img | video)",
        "hasImage": "boolean",
        "imageVariants": [{ "url": "string", "width": "number", "height": "number", "format": "string" }],
        "videoUrl": "string | null",
        "text": "string | null"
      }
    ],
    "optionGroups": [
      {
        "optionGroupId": "number",
        "type": "string (enum: REQUIRED | OPTIONAL | TEXT)",
        "name": "string",
        "choices": [
          {
            "choiceId": "number",
            "name": "string",
            "priceDelta": "number",
            "stock": "number"
          }
        ]
      }
    ],
    "images": [
      {
        "imageId": "string",
        "alt": "string",
        "variants": [
          {
            "url": "string",
            "width": "number",
            "height": "number",
            "format": "string"
          }
        ]
      }
    ],
    "artisan": {
      "artisanId": "number",
      "businessName": "string",
      "introduction": "string",
      "certificationLevel": "string (enum: 보유자 | 전승교육사 | 이수자 | 일반 — AI 내부 매핑: NATIONAL_INTANGIBLE_HERITAGE | MASTER_CRAFTSMAN | SENIOR_CRAFTSMAN | YOUNG_CRAFTSMAN)"
    }
  }
}
```

**Error**

```json
{
  "success": false,
  "status": 404,
  "errorCode": "NOT_FOUND"
}
```

---

#### 5-3. 상품 검색 `GET /api/products?keyword={keyword}`

5-1과 동일한 Request / Response 구조. `keyword` 쿼리 파라미터에 검색어를 전달.

---

#### 5-4. 주문 생성 `POST /api/payments/orders`

**Request**

```json
{
  "method": "POST",
  "url": "/api/payments/orders",
  "headers": {
    "Content-Type": "application/json",
    "Authorization": "Bearer {accessToken}"
  },
  "body": {
    "cartItemIds": ["number · 필수"],
    "addressId": "number · 필수",
    "paymentMethod": "string (enum: CARD 등) · 필수"
  }
}
```

**Response** `201 Created`

```json
{
  "success": true,
  "status": 201,
  "data": {
    "orderId": "number",
    "orderNumber": "string (예: ORD20260825001)",
    "status": "string (enum: CREATED | PAID | PAYMENT_FAILED | CANCELED | DELIVERED)",
    "totalAmount": "number",
    "createdAt": "string (ISO 8601)",
    "items": [
      {
        "orderItemId": "number",
        "productId": "number",
        "productName": "string",
        "price": "number",
        "quantity": "number"
      }
    ],
    "address": {
      "addressId": "number",
      "recipientName": "string",
      "phone": "string",
      "zipCode": "string",
      "address1": "string",
      "address2": "string",
      "isDefault": "boolean"
    }
  }
}
```

**Error**

```json
{
  "success": false,
  "status": 422,
  "errorCode": "BUSINESS_RULE_VIOLATION"
}
```

---

#### 5-5. AI 추천 챗봇 메시지 전송 `POST /api/chatbot/sessions/{sessionId}/messages`

> 세션은 `POST /api/chatbot/sessions` 로 먼저 생성 후 sessionId를 사용한다.

**Request**

```json
{
  "method": "POST",
  "url": "/api/chatbot/sessions/{sessionId}/messages",
  "headers": {
    "Content-Type": "application/json"
  },
  "body": {
    "message": "string · 필수 — 소비자 자연어 원문 (가공 없이 그대로 전달)"
  }
}
```

**Response** `200 OK`

```json
{
  "success": true,
  "status": 200,
  "data": {
    "sessionId": "string",
    "messageId": "number",
    "reply": "string — 챗봇 대화형 응답 텍스트",
    "intent": "string — AI 의도분류 결과",
    "suggestions": [
      "string — 후속 질문 제안 (최대 3개)"
    ],
    "products": [
      {
        "productId": "number",
        "name": "string",
        "price": "number",
        "thumbnail": [{ "url": "string", "width": "number", "height": "number", "format": "string" }],
        "status": "string",
        "category": "string",
        "rating": "number | null",
        "artisanId": "number",
        "artisanName": "string",
        "reason": "string — AI가 생성한 추천 이유"
      }
    ]
  }
}
```

**Error**

```json
{
  "success": false,
  "status": 404,
  "errorCode": "NOT_FOUND"
}
```

---

#### 5-6. AI 상세페이지 생성 요청 `POST /api/content/products/{productId}/generations`

**Request**

```json
{
  "method": "POST",
  "url": "/api/content/products/{productId}/generations",
  "headers": {
    "Content-Type": "application/json",
    "Authorization": "Bearer {accessToken}"
  },
  "body": {
    "images": ["string · 필수 — imageId 목록 (JPG/PNG, 3~12장, 10MB 이내)"],
    "productName": "string · 필수",
    "howMade": "string · 필수 — 어떻게 만드셨나요",
    "careTips": "string · 필수 — 관리법"
  }
}
```

**Response** `202 Accepted`

```json
{
  "success": true,
  "status": 202,
  "data": {
    "generationId": "number",
    "productId": "number",
    "status": "string (enum: PROCESSING | COMPLETED | FAILED)",
    "requestedAt": "string (ISO 8601)",
    "completedAt": "string (ISO 8601) | null"
  }
}
```

> 생성은 비동기. 완료 여부는 `GET /api/content/products/{productId}/generations/{generationId}` 로 폴링하거나 WebSocket 이벤트로 수신한다.

---

#### 5-7. 챗봇 세션 생성 `POST /api/chatbot/sessions`

**Request**

```json
{
  "method": "POST",
  "url": "/api/chatbot/sessions",
  "headers": {
    "Content-Type": "application/json"
  },
  "body": {}
}
```

**Response** `201 Created`

```json
{
  "success": true,
  "status": 201,
  "data": {
    "sessionId": "string",
    "expiresInSeconds": "number"
  }
}
```

---

#### 5-8. 장바구니 담기 `POST /api/payments/cart/items`

> 비로그인(게스트) 허용. 게스트 cart key는 클라이언트가 로컬스토리지로 관리하고 로그인 시 `POST /api/payments/cart/merge`로 병합한다.

**Request**

```json
{
  "method": "POST",
  "url": "/api/payments/cart/items",
  "headers": {
    "Content-Type": "application/json"
  },
  "body": {
    "productId": "number · 필수",
    "quantity": "number · 필수",
    "selectedOptions": [
      {
        "optionGroupId": "number · 필수",
        "choiceId": "number · 필수"
      }
    ]
  }
}
```

**Response** `201 Created`

```json
{
  "success": true,
  "status": 201,
  "data": {
    "cartItemId": "number",
    "productId": "number",
    "productName": "string",
    "quantity": "number",
    "unitPrice": "number",
    "totalPrice": "number",
    "thumbnail": [{ "url": "string", "width": "number", "height": "number", "format": "string" }],
    "selectedOptions": [
      {
        "optionGroupId": "number",
        "optionGroupName": "string",
        "choiceId": "number",
        "choiceName": "string",
        "priceDelta": "number"
      }
    ]
  }
}
```

**Error**

```json
{
  "success": false,
  "status": 422,
  "errorCode": "BUSINESS_RULE_VIOLATION"
}
```

---

#### 5-9. 장바구니 병합 `POST /api/payments/cart/merge`

> 로그인 직후 게스트 카트를 회원 카트로 병합한다. 동일 상품은 수량을 합산한다.

**Request**

```json
{
  "method": "POST",
  "url": "/api/payments/cart/merge",
  "headers": {
    "Content-Type": "application/json",
    "Authorization": "Bearer {accessToken}"
  },
  "body": {
    "guestCartItems": [
      {
        "productId": "number · 필수",
        "quantity": "number · 필수",
        "selectedOptions": [
          {
            "optionGroupId": "number",
            "choiceId": "number"
          }
        ]
      }
    ]
  }
}
```

**Response** `200 OK`

```json
{
  "success": true,
  "status": 200,
  "data": {
    "mergedCount": "number",
    "sections": [
      {
        "artisanId": "number",
        "artisanName": "string",
        "certificationLevel": "string",
        "items": [
          {
            "cartItemId": "number",
            "productId": "number",
            "productName": "string",
            "quantity": "number",
            "unitPrice": "number",
            "totalPrice": "number",
            "thumbnail": [{ "url": "string", "width": "number", "height": "number", "format": "string" }]
          }
        ]
      }
    ]
  }
}
```

---

#### 5-10. 결제 준비 `POST /api/payments`

> 토스페이먼츠 위젯 초기화에 필요한 `tossClientKey`와 `paymentId`를 반환한다. FE는 이 값으로 위젯을 마운트한 후 사용자가 결제 수단을 선택하면 `POST /api/payments/confirm`을 호출한다.

**Request**

```json
{
  "method": "POST",
  "url": "/api/payments",
  "headers": {
    "Content-Type": "application/json",
    "Authorization": "Bearer {accessToken}"
  },
  "body": {
    "orderId": "number · 필수"
  }
}
```

**Response** `200 OK`

```json
{
  "success": true,
  "status": 200,
  "data": {
    "paymentId": "string",
    "orderId": "number",
    "amount": "number",
    "tossClientKey": "string"
  }
}
```

**Error**

```json
{
  "success": false,
  "status": 404,
  "errorCode": "NOT_FOUND"
}
```

---

#### 5-11. 결제 승인 `POST /api/payments/confirm`

> 토스페이먼츠 결제 완료 후 FE가 전달받은 `paymentKey`, `orderId`, `amount`를 그대로 BE로 전달한다. BE는 토스페이먼츠 서버 측 승인 API를 호출하여 검증한다.

**Request**

```json
{
  "method": "POST",
  "url": "/api/payments/confirm",
  "headers": {
    "Content-Type": "application/json",
    "Authorization": "Bearer {accessToken}"
  },
  "body": {
    "paymentKey": "string · 필수 — 토스페이먼츠가 발급한 결제 키",
    "orderId": "number · 필수",
    "amount": "number · 필수"
  }
}
```

**Response** `200 OK`

```json
{
  "success": true,
  "status": 200,
  "data": {
    "paymentId": "string",
    "orderId": "number",
    "amount": "number",
    "method": "string (예: 카드)",
    "status": "string (enum: DONE | CANCELED | PARTIAL_CANCELED | ABORTED | EXPIRED)",
    "approvedAt": "string (ISO 8601)"
  }
}
```

**Error**

```json
{
  "success": false,
  "status": 400,
  "errorCode": "MISMATCH"
}
```

---

#### 5-12. 주문 목록 조회 `GET /api/member/me/orders`

**Request**

```json
{
  "method": "GET",
  "url": "/api/member/me/orders",
  "headers": {
    "Authorization": "Bearer {accessToken}"
  },
  "queryParams": {
    "cursor": "string · 선택",
    "limit": "number · 선택 (기본 20)"
  }
}
```

**Response** `200 OK`

```json
{
  "success": true,
  "status": 200,
  "data": {
    "items": [
      {
        "orderId": "number",
        "orderNumber": "string",
        "status": "string (enum: CREATED | PAID | PAYMENT_FAILED | CANCELED | DELIVERED)",
        "totalAmount": "number",
        "createdAt": "string (ISO 8601)",
        "items": [
          {
            "orderItemId": "number",
            "productId": "number",
            "productName": "string",
            "price": "number",
            "quantity": "number",
            "thumbnail": [{ "url": "string", "width": "number", "height": "number", "format": "string" }]
          }
        ]
      }
    ],
    "nextCursor": "string | null",
    "hasNext": "boolean"
  }
}
```

---

#### 5-13. 주문 상세 조회 `GET /api/member/me/orders/{orderId}`

**Request**

```json
{
  "method": "GET",
  "url": "/api/member/me/orders/{orderId}",
  "headers": {
    "Authorization": "Bearer {accessToken}"
  }
}
```

**Response** `200 OK`

```json
{
  "success": true,
  "status": 200,
  "data": {
    "orderId": "number",
    "orderNumber": "string",
    "status": "string (enum: CREATED | PAID | PAYMENT_FAILED | CANCELED | DELIVERED)",
    "totalAmount": "number",
    "createdAt": "string (ISO 8601)",
    "items": [
      {
        "orderItemId": "number",
        "productId": "number",
        "productName": "string",
        "price": "number",
        "quantity": "number",
        "thumbnail": [{ "url": "string", "width": "number", "height": "number", "format": "string" }]
      }
    ],
    "address": {
      "recipientName": "string",
      "phone": "string",
      "zipCode": "string",
      "address1": "string",
      "address2": "string"
    },
    "payment": {
      "paymentId": "string",
      "method": "string",
      "status": "string",
      "approvedAt": "string (ISO 8601) | null"
    }
  }
}
```

**Error**

```json
{
  "success": false,
  "status": 404,
  "errorCode": "NOT_FOUND"
}
```

---

#### 5-14. 배송 조회 `GET /api/payments/orders/{orderId}/delivery`

**Request**

```json
{
  "method": "GET",
  "url": "/api/payments/orders/{orderId}/delivery",
  "headers": {
    "Authorization": "Bearer {accessToken}"
  }
}
```

**Response** `200 OK`

```json
{
  "success": true,
  "status": 200,
  "data": {
    "orderId": "number",
    "carrier": "string (예: CJ대한통운)",
    "trackingNumber": "string",
    "status": "string (예: SHIPPED | IN_TRANSIT | DELIVERED)"
  }
}
```

**Error**

```json
{
  "success": false,
  "status": 404,
  "errorCode": "NOT_FOUND"
}
```

---

#### 5-15. 반품 신청 `POST /api/payments/returns`

> **주의:** 반품(return) 상태는 응답의 `status: REQUESTED`로 표현된다. 주문(order) 상태 Enum(`CREATED|PAID|PAYMENT_FAILED|CANCELED|DELIVERED`)과 별도 관리된다. 반품 수명주기 확장이 필요한 경우 BE 협의 필요.

**Request**

```json
{
  "method": "POST",
  "url": "/api/payments/returns",
  "headers": {
    "Content-Type": "application/json",
    "Authorization": "Bearer {accessToken}"
  },
  "body": {
    "orderId": "number · 필수",
    "reason": "string · 필수 (enum: CHANGE_OF_MIND | DEFECTIVE | WRONG_ITEM | OTHER)",
    "type": "string · 필수 (enum: RETURN | EXCHANGE)",
    "description": "string · 선택",
    "returnPhotoKeys": ["string · 선택 — S3 presigned PUT 완료 후 object key 목록"]
  }
}
```

**Response** `201 Created`

```json
{
  "success": true,
  "status": 201,
  "data": {
    "returnId": "number",
    "orderId": "number",
    "type": "string (enum: RETURN | EXCHANGE)",
    "status": "string (REQUESTED)",
    "requestedAt": "string (ISO 8601)"
  }
}
```

**Error**

```json
{
  "success": false,
  "status": 422,
  "errorCode": "BUSINESS_RULE_VIOLATION"
}
```

---

#### 5-16. 상품 찜 등록 `POST /api/products/{productId}/wish`

**Request**

```json
{
  "method": "POST",
  "url": "/api/products/{productId}/wish",
  "headers": {
    "Authorization": "Bearer {accessToken}"
  }
}
```

**Response** `201 Created`

```json
{
  "success": true,
  "status": 201,
  "data": null
}
```

**Error**

```json
{
  "success": false,
  "status": 409,
  "errorCode": "CONFLICT"
}
```

---

#### 5-17. 상품 찜 취소 `DELETE /api/products/{productId}/wish`

**Request**

```json
{
  "method": "DELETE",
  "url": "/api/products/{productId}/wish",
  "headers": {
    "Authorization": "Bearer {accessToken}"
  }
}
```

**Response** `200 OK`

```json
{
  "success": true,
  "status": 200,
  "data": null
}
```

**Error**

```json
{
  "success": false,
  "status": 404,
  "errorCode": "NOT_FOUND"
}
```

---

#### 5-18. 찜 목록 조회 `GET /api/member/me/wishes`

**Request**

```json
{
  "method": "GET",
  "url": "/api/member/me/wishes",
  "headers": {
    "Authorization": "Bearer {accessToken}"
  },
  "queryParams": {
    "cursor": "string · 선택",
    "limit": "number · 선택 (기본 20)"
  }
}
```

**Response** `200 OK`

```json
{
  "success": true,
  "status": 200,
  "data": {
    "items": [
      {
        "productId": "number",
        "name": "string",
        "price": "number",
        "thumbnail": [{ "url": "string", "width": "number", "height": "number", "format": "string" }],
        "status": "string (enum: ON_SALE | SOLD_OUT | HIDDEN | DRAFT)",
        "artisanId": "number",
        "artisanName": "string",
        "wishedAt": "string (ISO 8601)"
      }
    ],
    "nextCursor": "string | null",
    "hasNext": "boolean"
  }
}
```

---

### 6. 개발 연동 기준

| No. | 정의 항목 | 합의 내용 |
| --- | --- | --- |
| 6-1 | Mock API 제공 여부 | BE가 Mock 서버 제공 (미정) |
| 6-2 | Mock 데이터 제공 방식 | JSON 파일 또는 Mock 서버 URL 공유 |
| 6-3 | API 개발 완료 기준 | 단위 테스트 통과 + Notion/REST Docs 명세 업데이트 |
| 6-4 | FE 연동 가능 시점 | BE API 개발 완료 + Mock 데이터 제공 후 |
| 6-5 | Breaking Change 처리 | Request/Response 필드 추가·삭제·타입 변경 시 사전 공지 필수 |
| 6-6 | API 변경 공지 방식 | 팀 채널(Slack/Discord) 공지 + Notion 명세 즉시 업데이트 |
