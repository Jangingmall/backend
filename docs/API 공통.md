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

목록 API는 Cursor 기반 페이지네이션 적용.

| **파라미터** | **타입** | **필수** | **설명** |
| --- | --- | --- | --- |
| `cursor` | String | N | 이전 응답의 `nextCursor` 값 |
| `limit` | Integer | N | 페이지 크기 (기본 20, 최대 100) |

#### **목록 응답 구조**

```json
{
  "items": [],
  "nextCursor": "eyJpZCI6MTAwfQ==",
  "hasNext": true,
  "totalCount": 342
}
```

#### 권한

- 사용자는 ROLE { “USER” }
- 장인(판매자) 는 ROLE { “USER”,”ARTISAN” }
- 관리자는 ROLE { “USER”,”ADMIN” }
- Authenticated - jwt 인증만 있으면 통과