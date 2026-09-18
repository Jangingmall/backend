# AI 챗봇 연동 가이드

백엔드와 AI 챗봇 서버 간 연동 방식을 설명합니다.

---

## 1. 전체 흐름

```
[소비자]
  │ 자연어 입력 ("엄마 환갑 선물 5만원대 고급스러운 걸로")
  ▼
[FE] ─── POST /api/chatbot/sessions/{sessionId}/messages ───▶ [BE]
  ▲                                                             │ ① 메시지·세션 저장
  │                                                             │ ② POST /ai/chat 호출
  │                                                             ▼
  │                                                          [AI 서버]
  │                                                             │ 의도분류 → 임베딩 → 검색 → 랭킹
  │                                                             │ { reply, intent, product_ids, suggestions }
  │                                                             ▼
  │ ◀── 카드 조립(product_id로 상품 조회 + reply 결합) ──── [BE]
  ▼
[모달 렌더링]
```

---

## 2. BE 챗봇 API 목록

| 메서드 | 경로 | 설명 |
|--------|------|------|
| `POST` | `/api/chatbot/sessions` | 세션 생성 |
| `POST` | `/api/chatbot/sessions/{sessionId}/messages` | 메시지 전송 + AI 추천 |
| `GET` | `/api/chatbot/sessions/{sessionId}/messages` | 대화 이력 조회 |
| `DELETE` | `/api/chatbot/sessions/{sessionId}` | 세션 종료 |

---

## 3. BE → AI 호출: `POST /ai/chat`

메시지 전송 시 BE가 AI 서버에 내부 호출합니다.

**Request**

```json
{
  "session_id": "abc123",
  "message": "엄마 환갑 선물인데 고급스러운 걸로 5만원대",
  "history": [
    { "sender": "USER", "content": "이전 질문" },
    { "sender": "ARTISAN", "content": "이전 응답" }
  ]
}
```

| 필드 | 필수 | 설명 |
|------|------|------|
| `session_id` | ✅ | 세션 식별자 |
| `message` | ✅ | 소비자 원문. **BE가 임의 가공 금지** — 의도분류 정확도 보호 |
| `history` | ⬜ | 최근 5~6턴 상한. `sender`는 `"USER"` 또는 그 외(챗봇 발화) |

> `history`의 형식은 `{sender, content}` — `{role, content}` 아님. AI 서버는 `sender=="USER"`만 user로, 나머지는 assistant로 변환한다.

**Response**

```json
{
  "reply": "고급스러운 환갑 선물이라면 오래 두고 쓰실 작품이 좋겠어요...",
  "intent": "gift_recommendation",
  "product_ids": [78, 816, 52],
  "suggestions": ["3만 원 아래로", "다른 종류로", "포장되는 것만"]
}
```

| 필드 | 설명 |
|------|------|
| `reply` | 대화형 추천 코멘트. 항상 존재 |
| `intent` | 분류된 의도 (`gift_recommendation`, `product_search` 등) |
| `product_ids` | 추천 상품 ID 배열. 최대 3개. 없으면 `[]` |
| `suggestions` | 후속 제안 칩. 최대 3개 |

**BE 처리 규칙**

- `product_ids`가 `[]`이면 정상 응답 — 에러 아님
- AI 호출 실패·타임아웃(30초) → 최대 2회 재시도 후 `"현재 AI 추천을 이용할 수 없습니다"` fallback 반환
- 조건 추출(가격·선물테마·색상 등)은 AI 역할 — BE는 원문만 전달

---

## 4. 상품 동기화 (BE → AI)

| 이벤트 | BE API | AI 엔드포인트 |
|--------|--------|--------------|
| 상품 게시 | `POST /api/content/products/{productId}/publish` | `POST /ai/products/sync` |
| 상품 수정 | `PATCH /api/products/{productId}` | `PUT /ai/products/{id}` |
| 상품 삭제 | `DELETE /api/products/{productId}` | `DELETE /ai/products/{id}` |

동기화 실패 시 오류 로그만 기록하고 상품 처리는 계속 진행됩니다.

### POST /ai/products/sync Request

```json
{
  "artisan": {
    "artisan_id": 10,
    "name": "김도공방",
    "certification_level": "보유자",
    "introduction": "3대째 이천에서 청자를 굽습니다..."
  },
  "product": {
    "product_id": 1,
    "title": "청자 상감 다완",
    "category": "POTTERY",
    "material": "청자토",
    "price": 85000,
    "gift_theme": ["BIRTHDAY_60TH"],
    "purpose_tags": ["다도"],
    "making_story": "물레로 형태를 잡은 뒤...",
    "usage_care": "차를 우린 뒤 미지근한 물로...",
    "production_period_days": 14,
    "color": ["BLUE"]
  }
}
```

> `making_story`·`usage_care`는 AI 추천 품질을 직접 좌우하는 핵심 서사 데이터입니다.

---

## 5. 인증 구조

| 경로 | 인증 방식 |
|------|----------|
| `/ai/chat`, `/ai/products/**` | — (AI 서버 내부 — BE에서 직접 호출) |
| BE `/api/chatbot/**` | JWT Bearer Token (소비자) |

---

## 6. 환경 변수

| 변수명 | 설명 |
|--------|------|
| `AI_CHAT_BOT_URL` | AI 챗봇 서버 URL (기본: `http://localhost:8001`) |
| `AI_TIMEOUT_SECONDS` | 챗봇 요청 타임아웃 초 (기본: `300`) |
