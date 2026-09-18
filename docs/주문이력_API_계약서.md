# 주문 이력 API 계약서 — BE × FE

---

## 1. 문서 정보

| 버전 | 일자 | 변경 내용 | 작성자 |
|---|---|---|---|
| v1.0 | 2026-09-18 | 최초 작성 — 2.1~2.5 요청사항 반영 | BE (강정훈) |
| v1.1 | 2026-09-18 | 전 항목 구현 완료 반영. thumbnail 배열 형식 확정. IN_DELIVERY 상태 추가 | BE (강정훈) |

---

## 2. 확정 사항

- **2.5**: A안 확정 — 기존 `order_return.type` · `order_return.status` 활용, 응답에 `returnInfo` 추가. 스키마 변경 없음.
- **2.1~2.4**: 구현 완료.
- **thumbnail 형식**: `thumbnail: [{ "url": "..." }]` 배열. 이미지 없으면 빈 배열 `[]`. (`thumbnailUrl` 단일 문자열 아님 — FE MSW 목업 업데이트 필요)
- **IN_DELIVERY 상태 신규 추가**: `OrderStatus` 에 `IN_DELIVERY` (배송 중) 추가. 장인이 발송 등록 시 `PAID → IN_DELIVERY`, 배송 완료 확인 시 `IN_DELIVERY → DELIVERED`.

---

## 3. 엔드포인트 계약

### 3-1. 주문 목록 조회

```
GET /api/member/me/orders
Authorization: Bearer {accessToken}
```

#### 쿼리 파라미터

| 파라미터 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `status` | string | 선택 | 주문 상태 필터. 기본: `ALL` |
| `from` | string (ISO 8601 date) | 선택 | 조회 시작일 (포함). 예: `2026-01-01` |
| `to` | string (ISO 8601 date) | 선택 | 조회 종료일 (포함). 예: `2026-09-18` |
| `artisanName` | string | 선택 | 장인명 부분 일치 검색 |
| `page` | integer | 선택 | 0-based. 기본: `0` |
| `size` | integer | 선택 | 기본: `20` |

> `status` 허용값: `ALL` · `CREATED` · `PAID` · `PAYMENT_FAILED` · `CANCELED` · `IN_DELIVERY` · `DELIVERED` · `RETURN_REQUESTED`

#### 응답

```json
{
  "success": true,
  "status": 200,
  "data": {
    "content": [
      {
        "orderId": 1,
        "orderNumber": "ORD20260101001",
        "status": "RETURN_REQUESTED",
        "totalAmount": 85000,
        "createdAt": "2026-09-01T10:00:00",
        "items": [
          {
            "orderItemId": 10,
            "productId": 5,
            "productName": "청자 다완",
            "price": 85000,
            "quantity": 1,
            "thumbnail": [{ "url": "https://cdn.midam.store/products/abc.jpg" }]
          }
        ],
        "returnInfo": {
          "type": "EXCHANGE",
          "status": "REQUESTED"
        }
      }
    ],
    "totalElements": 1,
    "totalPages": 1,
    "size": 20,
    "number": 0,
    "first": true,
    "last": true,
    "empty": false
  }
}
```

> - `returnInfo`: `status = RETURN_REQUESTED`인 주문에만 포함. 그 외 주문은 필드 자체가 없음 (null 아님).
> - `items`: 주문에 포함된 전체 상품 배열. 다중 상품 주문은 모두 포함.
> - `thumbnail`: CDN URL 배열. 이미지 없으면 빈 배열 `[]`.

---

### 3-2. 주문 상세 조회

```
GET /api/member/me/orders/{orderId}
Authorization: Bearer {accessToken}
```

#### 경로 파라미터

| 파라미터 | 타입 | 설명 |
|---|---|---|
| `orderId` | integer | 주문 ID |

#### 응답

```json
{
  "success": true,
  "status": 200,
  "data": {
    "orderId": 1,
    "orderNumber": "ORD20260101001",
    "status": "RETURN_REQUESTED",
    "totalAmount": 85000,
    "createdAt": "2026-09-01T10:00:00",
    "items": [
      {
        "orderItemId": 10,
        "productId": 5,
        "productName": "청자 다완",
        "price": 85000,
        "quantity": 1,
        "thumbnail": [{ "url": "https://cdn.midam.store/products/abc.jpg" }]
      }
    ],
    "returnInfo": {
      "type": "RETURN",
      "status": "APPROVED"
    },
    "address": {
      "addressId": 3,
      "recipientName": "홍길동",
      "phone": "01012345678",
      "zipCode": "06000",
      "address1": "서울 강남구 테헤란로 1",
      "address2": "101호",
      "isDefault": false
    }
  }
}
```

> - `returnInfo`: `RETURN_REQUESTED` 주문에만 포함. 그 외 필드 자체가 없음.

---

### 3-3. 주문 상태별 집계 조회

```
GET /api/member/me/orders/summary
Authorization: Bearer {accessToken}
```

> 기준: 최근 3개월 고정

#### 응답

```json
{
  "success": true,
  "status": 200,
  "data": {
    "inProgress": {
      "awaitingPayment": 1,
      "preparing": 2,
      "inDelivery": 1,
      "delivered": 3
    },
    "closedCount": {
      "returnOrExchange": 1,
      "canceled": 2
    }
  }
}
```

| 필드 | 매핑 `OrderStatus` |
|---|---|
| `awaitingPayment` | `CREATED` |
| `preparing` | `PAID` |
| `inDelivery` | `IN_DELIVERY` |
| `delivered` | `DELIVERED` |
| `returnOrExchange` | `RETURN_REQUESTED` |
| `canceled` | `CANCELED` |

> `PAYMENT_FAILED`는 집계에서 제외됩니다.

---

## 4. OrderStatus 전체 값

| 값 | 의미 | 표시 배지 |
|---|---|---|
| `CREATED` | 결제 대기 | 결제대기 |
| `PAID` | 결제 완료 / 상품 준비 중 | 준비중 |
| `PAYMENT_FAILED` | 결제 실패 | 결제실패 |
| `CANCELED` | 취소 | 취소 |
| `IN_DELIVERY` | 배송 중 | 배송중 |
| `DELIVERED` | 배송 완료 | 배송완료 |
| `RETURN_REQUESTED` | 교환/환불 신청 | (→ Section 5 참조) |

---

## 5. 교환·환불 상태 매핑 (2.5 A안)

`OrderStatus = RETURN_REQUESTED`인 주문은 `returnInfo`로 Figma 8개 상태를 표현합니다.

| Figma 배지 | `returnInfo.type` | `returnInfo.status` |
|---|---|---|
| 교환 신청 | `EXCHANGE` | `REQUESTED` |
| 교환 불가 | `EXCHANGE` | `REJECTED` |
| 교환 승인 | `EXCHANGE` | `APPROVED` |
| 환불 신청 | `RETURN` | `REQUESTED` |
| 환불 불가 | `RETURN` | `REJECTED` |
| 환불 승인 | `RETURN` | `APPROVED` |
| 환불 완료 | `RETURN` | `COMPLETED` |
| 주문 취소 | — (`OrderStatus = CANCELED`, `returnInfo` 없음) | — |

### returnInfo 필드 설명

| 필드 | 타입 | 값 |
|---|---|---|
| `type` | string | `RETURN`(환불) \| `EXCHANGE`(교환) |
| `status` | string | `REQUESTED`(신청) \| `APPROVED`(승인) \| `REJECTED`(불가) \| `COMPLETED`(완료) |

---

## 6. 에러 코드

| HTTP | 발생 조건 |
|---|---|
| 400 | 허용되지 않는 `status` 값, 날짜 포맷 오류 (`from`/`to`) |
| 403 | USER 역할 없음 |
| 404 | 주문 없음 또는 본인 주문 아님 (상세 조회) |

---

## 7. 구현 현황

| 항목 | 상태 |
|---|---|
| 2.5 `returnInfo` (목록·상세) | ✅ 완료 |
| 2.1 목록 `items` + `thumbnail` | ✅ 완료 |
| 2.2 `from` / `to` 기간 필터 | ✅ 완료 |
| 2.3 `artisanName` 검색 | ✅ 완료 |
| 2.4 `GET /orders/summary` | ✅ 완료 |
| REST Docs 문서화 | ✅ 완료 |
| `IN_DELIVERY` 상태 추가 | ✅ 완료 |
