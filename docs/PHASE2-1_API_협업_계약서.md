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
| 4-7 | 주문 조회 | `GET` | `/api/member/me/orders/{orderId}` | USER | P1 |
| 4-8 | AI 추천 챗봇 메시지 전송 | `POST` | `/api/chatbot/sessions/{sessionId}/messages` | Public | P2 |
| 4-9 | AI 상세페이지 생성 요청 | `POST` | `/api/content/products/{productId}/generations` | ARTISAN | P0 |

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
    "giftTheme": "string (enum: HOUSEWARMING | WEDDING | PARENTS | PROMOTION | BIRTHDAY_60TH | BOSS | FRIEND | CORPORATE) · 선택",
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
        "thumbnailUrl": "string",
        "status": "string (enum: ON_SALE | SOLD_OUT | HIDDEN | DRAFT)",
        "category": "string",
        "color": "string",
        "giftTheme": "string",
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
    "thumbnailUrl": "string",
    "status": "string (enum: ON_SALE | SOLD_OUT | HIDDEN | DRAFT)",
    "category": "string",
    "color": "string",
    "giftTheme": "string",
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
        "imageUrl": "string | null",
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
      "certificationLevel": "string (enum: 보유자 | 전승교육사 | 이수자 | 일반)"
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
    "extracted": {
      "maxPrice": "number",
      "purpose": ["string"],
      "vibe": "string"
    },
    "products": [
      {
        "productId": "number",
        "name": "string",
        "price": "number",
        "thumbnailUrl": "string",
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

### 6. 개발 연동 기준

| No. | 정의 항목 | 합의 내용 |
| --- | --- | --- |
| 6-1 | Mock API 제공 여부 | BE가 Mock 서버 제공 (미정) |
| 6-2 | Mock 데이터 제공 방식 | JSON 파일 또는 Mock 서버 URL 공유 |
| 6-3 | API 개발 완료 기준 | 단위 테스트 통과 + Notion/REST Docs 명세 업데이트 |
| 6-4 | FE 연동 가능 시점 | BE API 개발 완료 + Mock 데이터 제공 후 |
| 6-5 | Breaking Change 처리 | Request/Response 필드 추가·삭제·타입 변경 시 사전 공지 필수 |
| 6-6 | API 변경 공지 방식 | 팀 채널(Slack/Discord) 공지 + Notion 명세 즉시 업데이트 |
