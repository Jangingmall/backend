## API 공통

---

### **HTTP 상태 코드**

| **코드** | **설명** |
| --- | --- |
| 200 | 성공 |
| 201 | 리소스 생성 성공 |
| 204 | 성공 (응답 본문 없음) |
| 400 | 요청 파라미터/바디 오류 |
| 401 | 인증 실패 (토큰 없음 또는 만료) |
| 403 | 권한 없음 |
| 404 | 리소스 없음 |
| 409 | 충돌 (중복, 이미 존재) |
| 410 | 리소스 만료 (예: 주문 만료) |
| 422 | 비즈니스 규칙 위반 |
| 429 | 요청 한도 초과 |
| 500 | 서버 내부 오류 |

### **공통 에러 코드**

| **에러 코드** | **HTTP** | **설명** |
| --- | --- | --- |
| `INVALID_INPUT` | 400 | 입력값 유효성 오류 |
| `REQUEST_INVALID` | 400 | Request Body 누락 |
| `REQUEST_BODY_MALFORMED` | 400 | JSON 형식 오류 |
| `UNAUTHORIZED` | 401 | 인증 필요 |
| `FORBIDDEN` | 403 | 접근 권한 없음 |
| `NOT_FOUND` | 404 | 리소스 없음 |
| `CONFLICT` | 409 | 리소스 충돌 (중복) |
| `BUSINESS_RULE_VIOLATION` | 422 | 비즈니스 규칙 위반 |
| `TOO_MANY_REQUESTS` | 429 | 요청 한도 초과 |
| `INTERNAL_ERROR` | 500 | 서버 내부 오류 |

### **페이지네이션 (공통)**

목록 API는 기본적으로 Cursor 기반 페이지네이션을 사용한다. 아래 11개 API는 `page` 파라미터를 추가로 지원하여 페이지 번호 UI(이전/다음/번호)에도 대응한다.

**page 모드 지원 API:** `/api/products`, `/api/products/me`, `/api/member/artisans`, `/api/member/recent-views`, `/api/member/me/orders`, `/api/member/me/wishes`, `/api/member/me/reviews`, `/api/member/me/reviews/writable`, `/api/admin/seller-applications`, `/api/products/{productId}/reviews`, `/api/products/{productId}/questions`

#### **Cursor 모드** (기본 — `cursor` 제공 또는 파라미터 없음)

| **파라미터** | **타입** | **필수** | **설명** |
| --- | --- | --- | --- |
| `cursor` | String | N | 이전 응답의 `nextCursor` 값. 미제공 시 첫 페이지 |
| `limit` | Integer | N | 페이지 크기 (기본 20, 최대 100) |

```json
{
  "items": [],
  "nextCursor": "eyJpZCI6MTAwfQ==",
  "hasNext": true,
  "totalCount": 342,
  "page": null,
  "totalPages": null
}
```

#### **Page 모드** (`page` 제공, `cursor` 미제공 — page 모드 지원 API 전용)

| **파라미터** | **타입** | **필수** | **설명** |
| --- | --- | --- | --- |
| `page` | Integer | N | 1-based 페이지 번호 (기본 1) |
| `limit` | Integer | N | 페이지 크기 (기본 20, 최대 100) |

```json
{
  "items": [],
  "nextCursor": null,
  "hasNext": true,
  "totalCount": 342,
  "page": 2,
  "totalPages": 18
}
```

> `cursor`와 `page`를 동시에 전달하면 `cursor`가 우선 적용된다.
> `/api/member/artisans`, `/api/member/recent-views`의 page 모드는 내부적으로 OFFSET을 사용하므로 대량 데이터 탐색 시 cursor 모드 대비 성능이 저하될 수 있다.

#### 권한

- 사용자는 ROLE { “USER” }
- 장인(판매자) 는 ROLE { “USER”,”ARTISAN” }
- 관리자는 ROLE { “USER”,”ADMIN” }
- Authenticated - jwt 인증만 있으면 통과