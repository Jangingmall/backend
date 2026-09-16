# AI팀 로컬 연동 가이드

백엔드를 로컬에서 실행하고 AI 서버와 연동하는 방법을 설명합니다.

## 1. 백엔드 실행

### 사전 조건

| 항목 | 버전 |
|------|------|
| Java | 25 |
| Redis | 7.x (로컬 실행 또는 Docker) |

Redis가 없으면 이메일 인증·토큰 저장이 동작하지 않습니다.

```bash
# Redis Docker로 실행
docker run -d -p 6379:6379 redis:7
```

### 실행

```bash
./gradlew bootRun --args='--spring.profiles.active=local-h2'
```

- 포트: `8080`
- DB: H2 인메모리 (PostgreSQL 모드)
- H2 콘솔: `http://localhost:8080/h2-console` (JDBC URL: `jdbc:h2:mem:jangingmall`)

---

## 2. AI 서버 URL 설정

백엔드는 기능별로 **두 개의 AI 서버 URL**을 분리해서 사용합니다.

| 환경변수 | 용도 | 기본값 |
|----------|------|--------|
| `AI_SGLANG_URL` | 챗봇 추천 (`POST /ai/chat`) | `http://localhost:8001` |
| `AI_OLLAMA_URL` | 콘텐츠 생성·상품 동기화 (`POST /ai/products`, `/ai/products/sync`, `PUT`, `DELETE`) | `http://localhost:8002` |

로컬 실행 시 포트가 다르다면 오버라이드합니다.

```bash
./gradlew bootRun --args='--spring.profiles.active=local-h2 --ai.sglang-url=http://localhost:YOUR_SGLANG_PORT --ai.ollama-url=http://localhost:YOUR_OLLAMA_PORT'
```

두 서버가 같은 호스트라면 동일한 URL을 지정해도 됩니다.

---

## 3. 백엔드 챗봇 API와의 접점

세션 관리·대화 이력은 백엔드 담당입니다. AI 서버는 `/ai/chat` 하나만 구현하면 됩니다.

| 메서드 | 경로 | AI 연동 |
|--------|------|---------|
| POST | /api/chatbot/sessions | — |
| POST | /api/chatbot/sessions/{sessionId}/messages | **내부에서 /ai/chat 호출** |
| GET | /api/chatbot/sessions/{sessionId}/messages | — |
| DELETE | /api/chatbot/sessions/{sessionId} | — |

---

## 4. AI 서버가 구현해야 할 API

### 4-1. 챗봇: `POST /ai/chat`

백엔드가 내부적으로 호출합니다. AI 서버가 이 엔드포인트를 제공해야 합니다.

**Request**

```json
{
  "session_id": "550e8400-e29b-41d4-a716-446655440000",
  "message": "엄마 환갑 선물 추천해줘",
  "history": [
    { "sender": "USER",  "content": "안녕하세요" },
    { "sender": "ADMIN", "content": "안녕하세요! 무엇을 도와드릴까요?" }
  ]
}
```

- `history`: 최근 6턴 이내 대화 이력 (오래된 순)

**Response**

```json
{
  "reply": "어머니께 도자기 찻잔 세트를 추천드립니다.",
  "intent": "GIFT_RECOMMENDATION",
  "product_ids": [1, 2, 5],
  "suggestions": [
    "3만원 이하로 보여줘",
    "목칠공예 작품도 있어?"
  ]
}
```

- `intent`: 자유 문자열 (예: `GIFT_RECOMMENDATION`, `PRODUCT_SEARCH`)
- `product_ids`: 백엔드 DB에 존재하는 상품 ID 배열. 없는 ID는 자동으로 제외됩니다.
- `suggestions`: 후속 질문 제안, 최대 3개
- 응답 실패·타임아웃 시 백엔드가 최대 2회 재시도 후 fallback 메시지를 반환합니다.

---

### 4-2. 콘텐츠 생성 요청: `POST /ai/products`

장인이 콘텐츠 생성을 요청할 때 백엔드가 호출합니다.

**Request**

```json
{
  "generationId": 42,
  "productId": 7,
  "images": [
    "https://cdn.example.com/img1.jpg",
    "https://cdn.example.com/img2.jpg"
  ],
  "productName": "청자 다완",
  "howMade": "전통 물레 성형 후 1280도 환원소성",
  "careTips": "중성세제로 손세척, 자연건조"
}
```

**Response**: 형식 자유 (백엔드에서 사용하지 않음)

생성이 완료되면 아래 콜백으로 결과를 전송합니다 (4-5 참조).

---

### 4-3. 상품 동기화: `POST /ai/products/sync`

상품이 게시(`ON_SALE`)될 때 백엔드가 자동으로 호출합니다.

**Request**

```json
{
  "artisan": {
    "artisan_id": 10,
    "business_name": "김도예공방",
    "certification_level": "명장",
    "region": "경기도 이천시"
  },
  "product": {
    "product_id": 7,
    "name": "청자 다완",
    "category_code": "도자공예",
    "subcategory_code": "다기",
    "material": "청자토",
    "price": 85000,
    "color": "WHITE",
    "gift_theme": ["PARENTS", "WEDDING"],
    "purpose_tags": ["다도", "선물"],
    "making_story": "전통 물레 성형 후 1280도 환원소성",
    "usage_care": "중성세제로 손세척, 자연건조",
    "production_period_days": 14,
    "status": "ON_SALE"
  }
}
```

**Response**: 2xx면 성공으로 처리 (body 무시)

---

### 4-4. 상품 수정 동기화: `PUT /ai/products/{productId}`

상품 정보가 수정될 때 백엔드가 자동으로 호출합니다.

**Request**

```json
{
  "product": {
    "name": "청자 다완 (개정판)",
    "category_code": "도자공예",
    "material": "청자토",
    "price": 90000,
    "color": "WHITE",
    "gift_theme": ["PARENTS"],
    "purpose_tags": ["다도"],
    "making_story": "전통 물레 성형",
    "usage_care": "손세척 권장",
    "production_period_days": 21
  }
}
```

**Response**: 2xx면 성공으로 처리

---

### 4-5. 상품 삭제 동기화: `DELETE /ai/products/{productId}`

상품이 삭제될 때 백엔드가 자동으로 호출합니다.

- Request body 없음
- Response: 2xx면 성공으로 처리

---

## 4. 백엔드가 제공하는 콜백 API

### `POST /internal/generations/{generationId}/complete`

AI 콘텐츠 생성이 완료됐을 때 AI 서버 → 백엔드로 호출합니다.
인증 불필요 (내부 전용 엔드포인트).

**Request**

```json
{
  "blocks": "[{\"tag\":\"h2\",\"text\":\"작품 소개\"},{\"tag\":\"p\",\"text\":\"60년 경력...\"}]"
}
```

- `blocks`: JSON 문자열 (배열을 stringify한 값)
- 각 블록 형식: `{ "tag": "h2"|"p"|"img"|"video", "text": "...", "imageUrl": "...", "videoUrl": "..." }`

**Response**

```json
{
  "success": true,
  "status": 200,
  "data": {
    "generationId": 42,
    "productId": 7,
    "status": "COMPLETED"
  }
}
```

---

## 5. 빠른 동작 확인

서버 실행 후 아래 순서로 연동을 확인할 수 있습니다.

```bash
# 1. 세션 생성 (토큰 없이도 동작)
curl -s -X POST http://localhost:8080/api/chatbot/sessions \
  -H "Authorization: Bearer <JWT>" | jq .

# 2. 메시지 전송 — AI 서버가 실행 중이면 실제 추천, 없으면 fallback
curl -s -X POST http://localhost:8080/api/chatbot/sessions/{sessionId}/messages \
  -H "Authorization: Bearer <JWT>" \
  -H "Content-Type: application/json" \
  -d '{"content": "엄마 환갑 선물 추천해줘"}' | jq .

# 3. 콘텐츠 생성 콜백 테스트
curl -s -X POST http://localhost:8080/internal/generations/1/complete \
  -H "Content-Type: application/json" \
  -d '{"blocks": "[{\"tag\":\"h2\",\"text\":\"작품 소개\"},{\"tag\":\"p\",\"text\":\"장인의 이야기\"}]"}' | jq .
```

JWT 발급은 `POST /api/member/signup` → `POST /api/member/login` 순서로 진행합니다.

---

## 6. 오류 / 타임아웃 동작

| 상황 | 백엔드 동작 |
|------|------------|
| AI `/ai/chat` 연결 실패 | 최대 2회 재시도 후 `"현재 AI 추천을 이용할 수 없습니다"` 반환 |
| AI `/ai/products/sync` 실패 | 오류 로그 기록 후 무시 (상품 게시 자체는 성공) |
| AI `/ai/products/{id}` 실패 | 오류 로그 기록 후 무시 |
| 타임아웃 | 기본 30초 (`ai.timeout-seconds` 설정 가능) |
