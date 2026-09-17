# AI팀 로컬 연동 가이드

백엔드를 로컬에서 실행하고 AI 서버와 연동하는 방법을 설명합니다.

---

## 1. 백엔드 실행

### 사전 조건

| 항목 | 버전 |
|------|------|
| Java | 25 |
| Redis | 8+ (Docker로 간단히 실행 가능) |

```bash
# Redis Docker 실행
docker run -d -p 6379:6379 redis:8
```

### `.env` 파일 설정

프로젝트 루트에 `.env` 파일을 생성합니다.

```dotenv
# AI 서버 URL (AI팀 서버 주소로 변경)
AI_SGLANG_URL=http://localhost:8001
AI_OLLAMA_URL=http://localhost:8002

# 기타 (local 프로파일 기본값이 있어 생략 가능)

```

- `AI_SGLANG_URL`: 챗봇 추천 서버 (`POST /ai/chat`)
- `AI_OLLAMA_URL`: 콘텐츠 생성·상품 동기화 서버 (`POST /ai/products`, sync, PUT, DELETE)

### 실행

```
./gradlew build 
./gradlew bootRun 
```
build : 테스트 및 문서 최신화  <br>
bootRun : 서버 실행

---

## 2. Dev 토큰 발급 (로컬 전용)

AI 팀은 회원가입·아티산 승인 플로우 없이 ARTISAN 토큰을 발급받을 수 있습니다.
이 엔드포인트는 **`local` 프로파일에서만 활성화**됩니다.

### 2-1. `POST /dev/setup` — 테스트 아티산 + 상품 자동 생성

처음 한 번 실행하면 DB에 개발용 아티산과 상품을 생성하고 artisanId, productId, 7일짜리 ARTISAN 토큰을 반환합니다.
이미 존재하면 기존 데이터를 재사용합니다 (멱등 실행).

```bash
curl -s -X POST http://localhost:8080/dev/setup | jq .
```
| jq 없으면 설치: brew install jq

### 2-2. `POST /dev/token` — 역할별 토큰 발급 (memberId 지정)

memberId를 직접 알고 있을 때 사용합니다.

```bash
# ARTISAN 토큰 (memberId 직접 지정)
curl -s -X POST "http://localhost:8080/dev/token?role=ARTISAN&memberId=1" | jq .

# ADMIN 토큰
curl -s -X POST "http://localhost:8080/dev/token?role=ADMIN&memberId=1" | jq .

# USER 토큰 (기본값)
curl -s -X POST "http://localhost:8080/dev/token?role=USER&memberId=1" | jq .
```

**Response 예시**

```json
{
  "success": true,
  "status": 200,
  "data": {
    "accessToken": "eyJhbGci...",
    "role": "ARTISAN",
    "memberId": 1
  }
}
```

> `/dev/setup` 이 반환한 `artisanId`를 `memberId`로 사용하면 product 소유권 체크를 통과합니다.

---

## 3. BE → AI → BE 전체 흐름 (콘텐츠 생성)

```
장인 (ARTISAN JWT)
  │
  └─► POST /api/content/products/{productId}/generations
        백엔드: ContentGeneration 저장 (status=PROCESSING), 202 반환
        백엔드: @Async로 POST /ai/products 호출 ──────────────────────────► AI 서버
                                                                              │  생성 완료 후
                                                                              ▼
        백엔드: POST /internal/generations/{generationId}/complete ◄──────── AI 서버
        백엔드: ContentGeneration.complete(), ContentBlock 저장
```

### 3-1. Step 1 — 생성 요청

```bash
# 먼저 /dev/setup 으로 받은 값 사용
ARTISAN_TOKEN="eyJhbGci..."
PRODUCT_ID=1

curl -s -X POST "http://localhost:8080/api/content/products/${PRODUCT_ID}/generations" \
  -H "Authorization: Bearer ${ARTISAN_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{
    "images": ["https://cdn.example.com/img1.jpg", "https://cdn.example.com/img2.jpg"],
    "productName": "청자 다완",
    "howMade": "전통 물레 성형 후 1280도 환원소성",
    "careTips": "중성세제로 손세척, 자연건조"
  }' | jq .
```

**Response (202 Accepted)**

```json
{
  "success": true,
  "status": 202,
  "data": {
    "generationId": 1,
    "productId": 1,
    "status": "PROCESSING",
    "requestedAt": "2026-09-16T10:00:00",
    "completedAt": null
  }
}
```

이후 백엔드는 비동기로 AI 서버에 `POST /ai/products`를 호출합니다. (`generationId` 기록)

### 3-2. Step 2 — AI 서버가 받는 요청 (`POST /ai/products`)

백엔드가 AI 서버로 전송하는 요청 형식입니다.

```json
{
  "generationId": 1,
  "productId": 1,
  "images": [
    "https://cdn.example.com/img1.jpg",
    "https://cdn.example.com/img2.jpg"
  ],
  "productName": "청자 다완",
  "howMade": "전통 물레 성형 후 1280도 환원소성",
  "careTips": "중성세제로 손세척, 자연건조"
}
```

**Response**: 형식 자유 (백엔드에서 사용하지 않음). 2xx / 4xx / 5xx 어느 쪽이든 콜백 방식으로 결과를 전달합니다.

### 3-3. Step 3 — AI 서버가 결과를 콜백 (`POST /internal/generations/{generationId}/complete`)

생성 완료 후 AI 서버 → 백엔드로 호출합니다.  
**인증 불필요** — `/internal/**` 경로는 퍼블릭입니다.

```bash
GENERATION_ID=1

curl -s -X POST "http://localhost:8080/internal/generations/${GENERATION_ID}/complete" \
  -H "Content-Type: application/json" \
  -d '{
    "reactDocument": {
      "schemaVersion": "2.0",
      "canvasWidth": 774,
      "root": [
        {
          "tag": "section",
          "props": {},
          "children": [
            { "tag": "h2", "props": {}, "children": [{ "tag": "text", "props": { "value": "청자 다완의 이야기" }, "children": [] }] },
            { "tag": "p",  "props": {}, "children": [{ "tag": "text", "props": { "value": "60년 경력 도예 장인이 빚은 작품입니다." }, "children": [] }] }
          ]
        }
      ]
    }
  }' | jq .
```

**reactDocument 필드 형식**: AI Pydantic 모델(`ReactDetailPageDocumentDto`)이 검증한 AST 객체입니다. 백엔드는 이 JSON을 그대로 저장·서빙합니다.

**Response (200 OK)**

```json
{
  "success": true,
  "status": 200,
  "data": {
    "generationId": 1,
    "productId": 1,
    "status": "COMPLETED",
    "requestedAt": "2026-09-16T10:00:00",
    "completedAt": "2026-09-16T10:01:30"
  }
}
```

### 3-4. Step 4 — 상태 폴링 (선택)

AI 서버가 콜백 전까지 상태를 확인하려면:

```bash
curl -s "http://localhost:8080/api/content/products/${PRODUCT_ID}/generations/${GENERATION_ID}" \
  -H "Authorization: Bearer ${ARTISAN_TOKEN}" | jq .data.status
```

- `PROCESSING`: 아직 생성 중
- `COMPLETED`: 완료
- `FAILED`: AI 서버 오류 또는 타임아웃

---

## 4. AI 서버가 구현해야 할 API 전체 목록

### 4-1. 챗봇: `POST /ai/chat`

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

**Response**

```json
{
  "reply": "어머니께 도자기 찻잔 세트를 추천드립니다.",
  "intent": "GIFT_RECOMMENDATION",
  "product_ids": [1, 2, 5],
  "suggestions": ["3만원 이하로 보여줘", "목칠공예 작품도 있어?"]
}
```

- 응답 실패·타임아웃 시 백엔드가 최대 2회 재시도 후 fallback 반환
- `product_ids`: 백엔드 DB에 없는 ID는 자동 제외

### 4-2. 콘텐츠 생성: `POST /ai/products`

→ 3-2 참조

### 4-3. 상품 동기화: `POST /ai/products/sync`

상품이 `ON_SALE` 상태로 변경될 때 자동 호출됩니다.

```json
{
  "artisan": {
    "artisan_id": 1,
    "name": "Dev 공방",
    "certification_level": "일반",
    "introduction": "개발 테스트용 공방"
  },
  "product": {
    "product_id": 1,
    "title": "[DEV] 청자 다완 테스트 상품",
    "category": null,
    "material": null,
    "price": 85000,
    "gift_theme": [],
    "purpose_tags": [],
    "making_story": null,
    "usage_care": null,
    "production_period_days": null,
    "color": []
  }
}
```

**Response**: 2xx면 성공. 실패 시 오류 로그만 기록 (상품 게시는 계속 진행됨)

### 4-4. 상품 수정 동기화: `PUT /ai/products/{productId}`

**Request**: 4-3의 `product` 객체와 동일한 구조  
**Response**: 2xx면 성공

### 4-5. 상품 삭제 동기화: `DELETE /ai/products/{productId}`

Request body 없음. Response: 2xx면 성공

---

## 5. 전체 흐름 빠른 검증

```bash
# 1. 서버 실행 확인
curl -s http://localhost:8080/healthz | jq .

# 2. Dev 아티산 + 상품 생성 및 토큰 발급 (결과에서 artisanId, productId, accessToken 기록)
curl -s -X POST http://localhost:8080/dev/setup | jq .

# 3. 콘텐츠 생성 요청 (ARTISAN_TOKEN과 PRODUCT_ID는 위 결과 사용)
curl -s -X POST "http://localhost:8080/api/content/products/1/generations" \
  -H "Authorization: Bearer <ARTISAN_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"images":[],"productName":"청자 다완","howMade":"전통 물레 성형","careTips":"손세척"}' | jq .

# 4. AI 서버 없이 콜백 직접 호출로 흐름 검증 (GENERATION_ID는 위 결과 사용)
curl -s -X POST "http://localhost:8080/internal/generations/1/complete" \
  -H "Content-Type: application/json" \
  -d '{"reactDocument":{"schemaVersion":"2.0","canvasWidth":774,"root":[]}}' | jq .

# 5. 상태 확인 → COMPLETED여야 정상
curl -s "http://localhost:8080/api/content/products/1/generations/1" \
  -H "Authorization: Bearer <ARTISAN_TOKEN>" | jq .data.status
```

> Step 3과 4 사이에 실제 AI 서버가 `/ai/products`를 받아서 콜백을 보내면, Step 4의 수동 콜백 없이도 자동으로 COMPLETED가 됩니다.

---

## 6. 오류 / 타임아웃 동작

| 상황 | 백엔드 동작 |
|------|------------|
| AI `/ai/chat` 연결 실패 | 최대 2회 재시도 후 `"현재 AI 추천을 이용할 수 없습니다"` 반환 |
| AI `/ai/products` 실패/타임아웃 | generation status → `FAILED`, 오류 로그 기록 |
| AI `/ai/products/sync` 실패 | 오류 로그 기록 후 무시 (상품 게시 자체는 성공) |
| 콜백 `/internal/...` 없이 AI 무응답 | `FAILED` — 백엔드가 다시 시도하지 않음. AI 서버가 콜백을 보내야 함 |
| 타임아웃 | 기본 300초 (`ai.timeout-seconds` 설정, 환경변수 `AI_TIMEOUT_SECONDS`로 조정 가능) |
