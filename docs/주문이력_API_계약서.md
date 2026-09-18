# 주문 이력 API 계약서 — BE × FE

---

## 1. 문서 정보

| 버전 | 일자 | 변경 내용 | 작성자 |
|---|---|---|---|
| v1.0 | 2026-09-18 | 최초 작성 — 2.1~2.5 요청사항 반영 | BE (강정훈) |

---

## 2. 배경 및 확정 사항

FE 요청(2.1~2.5)을 코드 기준으로 검토한 결과:

- **2.5 교환/환불 상태 세분화**: A안 확정 — 기존 `order_return` 테이블의 `type`·`status`를 그대로 활용, 응답에 `returnInfo` 객체 추가. 신규 Enum·스키마 변경 없음.
- **2.1~2.4**: 2.5 계약 확정 후 순차 구현 예정. FE는 MSW 목업으로 병행 개발 가능.

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
| `from` | string (ISO 8601 date) | 선택 | 조회 시작일. 예: `2026-01-01` |
| `to` | string (ISO 8601 date) | 선택 | 조회 종료일. 예: `2026-09-18` |
| `artisanName` | string | 선택 | 장인명 부분 일치 검색 |
| `page` | integer | 선택 | 0-based. 기본: `0` |
| `size` | integer | 선택 | 기본: `20` |

> `status` 허용값: `ALL` · `CREATED` · `PAID` · `PAYMENT_FAILED` · `CANCELED` · `DELIVERED` · `RETURN_REQUESTED`

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
        "createdAt": "2026-09-01T10:00:00Z",
        "returnInfo": {
          "type": "EXCHANGE",
          "status": "REQUESTED"
        },
        "items": [
          {
            "orderItemId": 10,
            "productId": 5,
            "productName": "청자 다완",
            "price": 85000,
            "quantity": 1,
            "thumbnailUrl": "https://cdn.midam.store/products/abc.jpg"
          }
        ]
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

> - `returnInfo`: `status`가 `RETURN_REQUESTED`인 주문에만 포함. 그 외 주문은 해당 필드 없음.
> - `items`: 주문에 포함된 전체 상품 배열. 다중 상품 주문은 모두 포함.
> - `thumbnailUrl`: CDN URL 또는 `null`.

---

### 3-2. 주문 상세 조회

```
GET /api/member/me/orders/{orderId}
Authorization: Bearer {accessToken}
```

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
    "createdAt": "2026-09-01T10:00:00Z",
    "returnInfo": {
      "type": "RETURN",
      "status": "APPROVED"
    },
    "items": [
      {
        "orderItemId": 10,
        "productId": 5,
        "productName": "청자 다완",
        "price": 85000,
        "quantity": 1,
        "thumbnailUrl": "https://cdn.midam.store/products/abc.jpg"
      }
    ],
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
      "inDelivery": 0,
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

---

## 4. 교환·환불 상태 매핑 (2.5 A안 확정)

`OrderStatus = RETURN_REQUESTED`인 주문은 `returnInfo`로 Figma 8개 상태를 모두 표현합니다.

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

---

## 5. 에러 코드

| HTTP | errorCode | 설명 |
|---|---|---|
| 400 | `INVALID_INPUT` | 허용되지 않는 `status` 값 또는 날짜 포맷 오류 |
| 403 | `FORBIDDEN` | USER 역할 없음 |
| 404 | `NOT_FOUND` | 해당 주문 없음 또는 본인 주문 아님 |

---

## 6. 구현 일정

| 항목 | 상태 |
|---|---|
| 2.5 `returnInfo` (목록·상세) | 구현 예정 |
| 2.1 목록 `items` + `thumbnailUrl` | 구현 예정 |
| 2.2 `from` / `to` 기간 필터 | 구현 예정 |
| 2.3 `artisanName` 검색 | 구현 예정 |
| 2.4 `GET /orders/summary` | 구현 예정 |

> FE는 위 계약을 기준으로 MSW 목업 구성 후 병행 개발 가능합니다.
