# AI팀 로컬 연동 가이드

백엔드를 로컬에서 실행하고 AI 서버와 연동하는 방법을 설명합니다.

---

## 1. 백엔드 실행

### 사전 조건

| 항목 | 버전 |
|------|------|
| Java | 25 |
| Redis | 8+ |

```bash
# Redis Docker 실행
docker run -d -p 6379:6379 redis:8
```

### `.env` 파일 설정

프로젝트 루트에 `.env` 파일을 생성합니다.

```dotenv
# AI 서버 URL (AI팀 서버 주소로 변경)
AI_CHAT_BOT_URL=http://localhost:8001
AI_CONTENT_URL=http://localhost:8002

# BE → AI 내부 인증 토큰 (BE가 AI 서버 /internal/** 호출 시 사용)
AI_INTERNAL_AUTH_TOKEN=your-shared-secret-here

# AI → BE 내부 인증 토큰 (AI가 BE /internal/** 콜백 시 사용)
BACKEND_AUTH_TOKEN=your-shared-secret-here
```

### 환경 변수 설명

| 변수명 | 용도 | 방향 |
|--------|------|------|
| `AI_CHAT_BOT_URL` | 챗봇 서버 URL | BE → AI |
| `AI_CONTENT_URL` | 콘텐츠 생성 서버 URL | BE → AI |
| `AI_INTERNAL_AUTH_TOKEN` | BE가 AI `POST /internal/v1/ai/detail-page-jobs` 호출 시 `X-AI-Internal-Token` 헤더 값 | BE → AI |
| `BACKEND_AUTH_TOKEN` | AI가 BE `/internal/**` 콜백 호출 시 `Authorization: Bearer` 값 | AI → BE |

> `BACKEND_AUTH_TOKEN`이 빈 문자열이면 `/internal/**` 경로는 **무조건 401**을 반환합니다. 반드시 설정하세요.

### 실행

```bash
./gradlew bootRun
```

빌드 + 문서 최신화까지 하려면:

```bash
./gradlew build && ./gradlew bootRun
```

---

## 2. Dev 토큰 발급 (로컬 전용)

AI 팀은 회원가입·아티산 승인 플로우 없이 ARTISAN 토큰을 발급받을 수 있습니다.  
이 엔드포인트는 **`local` 프로파일에서만 활성화**됩니다.

### 2-1. `POST /dev/setup` — 테스트 아티산 + 상품 자동 생성

처음 한 번 실행하면 DB에 개발용 아티산과 상품을 생성하고 `artisanId`, `productId`, 7일짜리 ARTISAN 토큰을 반환합니다.  
이미 존재하면 기존 데이터를 재사용합니다 (멱등 실행).

```bash
curl -s -X POST http://localhost:8080/dev/setup | jq .
```

> jq 없으면 설치: `brew install jq`

**Response 예시**

```json
{
  "success": true,
  "status": 200,
  "data": {
    "artisanId": 1,
    "productId": 1,
    "accessToken": "eyJhbGci..."
  }
}
```

### 2-2. `POST /dev/token` — 역할별 토큰 발급

`memberId`를 직접 알고 있을 때 사용합니다.

```bash
# ARTISAN 토큰
curl -s -X POST "http://localhost:8080/dev/token?role=ARTISAN&memberId=1" | jq .

# ADMIN 토큰
curl -s -X POST "http://localhost:8080/dev/token?role=ADMIN&memberId=1" | jq .

# USER 토큰
curl -s -X POST "http://localhost:8080/dev/token?role=USER&memberId=1" | jq .
```

> `/dev/setup`이 반환한 `artisanId`를 `memberId`로 사용하면 product 소유권 체크를 통과합니다.

---

## 3. BE → AI → BE 전체 흐름 (콘텐츠 생성)

```
장인 (ARTISAN JWT)
  │
  └─► POST /api/content/products/{productId}/generations
        백엔드: ContentGeneration 저장 (status=PROCESSING), 202 반환
        백엔드: @Async로 POST /internal/v1/ai/detail-page-jobs 호출 ──────► AI 서버
                X-AI-Internal-Token + Idempotency-Key 헤더 필수            │ 202 QUEUED 즉시 반환
                multipart: metadata JSON + product_image                    │
        백엔드: markQueued(jobId) → status=QUEUED                          │ 생성 완료 후
                                                                             ▼
        백엔드: POST /internal/generations/{generationId}/completion ◄──────── AI 서버
              Authorization: Bearer {BACKEND_AUTH_TOKEN}
              Idempotency-Key 헤더 필수
              multipart: metadata JSON + 이미지 바이너리
        백엔드: ContentGeneration.complete() → status=COMPLETED, S3에 이미지 저장
```

### 3-1. Step 1 — 생성 요청

```bash
ARTISAN_TOKEN="eyJhbGci..."   # /dev/setup 결과
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
    "requestedAt": "2026-09-18T10:00:00",
    "completedAt": null
  }
}
```

이후 백엔드가 비동기로 AI 서버에 job을 제출합니다. AI 서버가 202를 반환하면 status가 `QUEUED`로 바뀝니다.

### 3-2. Step 2 — BE가 AI 서버에 제출하는 요청 (`POST /internal/v1/ai/detail-page-jobs`)

백엔드가 AI 서버로 전송하는 요청 형식입니다.

**헤더**

| 헤더 | 값 |
|------|----|
| `X-AI-Internal-Token` | `AI_INTERNAL_AUTH_TOKEN` 환경변수 값 |
| `Idempotency-Key` | `generationId` 문자열 |
| `Content-Type` | `multipart/form-data` |

**form field: `metadata` (JSON)**

```json
{
  "product_id": "1",
  "idempotency_key": "1",
  "template_id": "default-long-detail-page",
  "locale": "ko-KR",
  "user_hints": {
    "product_name": "청자 다완",
    "making_method": "전통 물레 성형 후 1280도 환원소성",
    "care_guide": "중성세제로 손세척, 자연건조"
  },
  "options": {
    "source_generation_id": "1"
  }
}
```

**file: `product_image`** — 상품 이미지 첫 번째 URL에서 fetch한 바이너리

**Response (202)**

```json
{
  "product_id": "1",
  "job_id": "job-abc-123",
  "request_id": "req-abc-123",
  "status": "QUEUED",
  "status_url": "http://ai-server/status/job-abc-123",
  "created_at": "2026-09-18T10:00:01Z"
}
```

백엔드는 이 응답에서 `job_id`, `request_id`, `status_url`을 추출해 `markQueued()`로 저장합니다.

### 3-3. Step 3 — AI 서버가 결과를 콜백

생성 완료 후 AI 서버 → 백엔드로 호출합니다. 엔드포인트는 하나입니다.

| 엔드포인트 | 설명 |
|-----------|------|
| `POST /internal/generations/{generationId}/completion` | react_document JSON + 이미지 파일 전송 |

**인증**: `Authorization: Bearer {BACKEND_AUTH_TOKEN}` 헤더 필수  
**멱등성**: `Idempotency-Key` 헤더 필수 (job 제출 시 사용한 `generationId` 값)  
**Content-Type**: `multipart/form-data`

| Part 이름 | 종류 | 설명 |
|-----------|------|------|
| `metadata` | form field (JSON) | 생성 메타데이터 |
| `detail_page_image` | file (image/*, optional) | 전체 상세페이지 이미지 |
| `detail_page_section_02`, `03`, ... | file (image/*, optional) | 섹션별 이미지 |
| `product_photo_*` | file (image/*, optional) | 상품 사진 (`photo_id` 기반 파일명) |

**metadata JSON 형식**

```json
{
  "generationId": "1",
  "jobId": "job-abc-123",
  "requestId": "req-abc-123",
  "idempotencyKey": "1",
  "productId": "1",
  "detailPage": {
    "reactDocument": {
      "schemaVersion": "2.0",
      "canvasWidth": 774,
      "root": [
        {
          "tag": "section",
          "props": {},
          "children": [
            {
              "tag": "h2",
              "props": {},
              "children": [{ "tag": "text", "props": { "value": "청자 다완의 이야기" }, "children": [] }]
            }
          ]
        }
      ]
    }
  }
}
```

**curl 예시 (로컬 테스트용)**

```bash
BACKEND_AUTH_TOKEN="your-shared-secret-here"
GENERATION_ID=1

curl -s -X POST "http://localhost:8080/internal/generations/${GENERATION_ID}/completion" \
  -H "Authorization: Bearer ${BACKEND_AUTH_TOKEN}" \
  -H "Idempotency-Key: ${GENERATION_ID}" \
  -F 'metadata={"generationId":"1","jobId":"job-001","requestId":"req-001","idempotencyKey":"1","productId":"1","detailPage":{"reactDocument":{"schemaVersion":"2.0","canvasWidth":774,"root":[{"tag":"h2","props":{},"children":[{"tag":"text","props":{"value":"청자 다완의 이야기"},"children":[]}]}]}}}' \
  | jq .
```

**Response (200 OK)**

```json
{
  "success": true,
  "status": 200,
  "data": {
    "generation_id": "1",
    "product_id": "1",
    "status": "SAVED",
    "saved_at": "2026-09-18T10:01:30"
  }
}
```

중복 요청(같은 `Idempotency-Key`) 시:

```json
{
  "data": {
    "generation_id": "1",
    "product_id": "1",
    "status": "ALREADY_SAVED",
    "saved_at": "2026-09-18T10:01:30"
  }
}
```

> `ALREADY_SAVED`는 200으로 반환됩니다. 409를 반환하지 않습니다.

### 3-4. Step 4 — 상태 폴링 (선택)

```bash
curl -s "http://localhost:8080/api/content/products/${PRODUCT_ID}/generations/${GENERATION_ID}" \
  -H "Authorization: Bearer ${ARTISAN_TOKEN}" | jq .data.status
```

| 상태 | 의미 |
|------|------|
| `PROCESSING` | AI 서버에 job 제출 전 |
| `QUEUED` | AI 서버 job 제출 완료, 콜백 대기 중 |
| `COMPLETED` | 콜백 수신 완료, 콘텐츠 저장됨 |
| `FAILED` | AI 서버 오류 또는 job 제출 재시도 소진 |

---

## 4. 이미지 업로드 (Presigned URL 방식)

AI 서버가 이미지를 업로드할 때는 Presigned URL을 발급받아 S3에 직접 업로드합니다.

### 4-1. Presigned URL 발급 (`POST /api/images/presigned-url`)

**인증**: `Authorization: Bearer {BACKEND_AUTH_TOKEN}` 헤더 + 요청 body에 `memberId` 필수

```bash
BACKEND_AUTH_TOKEN="your-shared-secret-here"
MEMBER_ID=1   # 업로드 소유자 회원 ID

curl -s -X POST "http://localhost:8080/api/images/presigned-url" \
  -H "Authorization: Bearer ${BACKEND_AUTH_TOKEN}" \
  -H "Content-Type: application/json" \
  -d "{
    \"fileName\": \"product.webp\",
    \"contentType\": \"image/webp\",
    \"purpose\": \"PRODUCT\",
    \"sourceWidth\": 1200,
    \"sourceHeight\": 800,
    \"variants\": [\"320w\", \"640w\", \"1280w\"],
    \"memberId\": ${MEMBER_ID}
  }" | jq .
```

**Response (200 OK)**

```json
{
  "success": true,
  "status": 200,
  "data": {
    "imageId": "01JIMAGE000000000000000000",
    "uploads": [
      {
        "variant": "320w",
        "objectKey": "images/product/1/01JIMAGE.../320w.webp",
        "presignedUrl": "https://s3.amazonaws.com/..."
      }
    ],
    "expiresInSeconds": 300
  }
}
```

> `memberId`를 생략하거나 null이면 400 오류입니다. AI AGENT 역할은 항상 `memberId`를 명시해야 합니다.

### 4-2. S3 직접 업로드

발급받은 `presignedUrl`로 이미지를 직접 PUT합니다.

```bash
curl -X PUT "https://s3.amazonaws.com/..." \
  -H "Content-Type: image/webp" \
  --data-binary @image.webp
```

---

## 5. AI 서버가 구현해야 할 API 전체 목록

### 5-1. 챗봇: `POST /ai/chat`

**Request**

```json
{
  "session_id": "550e8400-e29b-41d4-a716-446655440000",
  "message": "엄마 환갑 선물 추천해줘",
  "history": [
    { "sender": "USER", "content": "안녕하세요" },
    { "sender": "ASSISTANT", "content": "안녕하세요! 무엇을 도와드릴까요?" }
  ]
}
```

> `history`는 최근 6턴까지만 포함됩니다.

**Response**

```json
{
  "reply": "어머니께 도자기 찻잔 세트를 추천드립니다.",
  "intent": "gift_recommendation",
  "product_ids": [1, 2, 3],
  "suggestions": ["가격대를 알려주세요", "어떤 스타일을 좋아하시나요?"]
}
```

- 응답 실패·타임아웃 시 백엔드가 최대 2회 재시도 후 `"현재 AI 추천을 이용할 수 없습니다"` fallback 반환
- `product_ids`가 `[]`이면 정상 응답 (에러 아님)

### 5-2. 콘텐츠 생성 job 수신: `POST /internal/v1/ai/detail-page-jobs`

→ 3-2 참조. 이 경로가 BE→AI 방향의 콘텐츠 생성 요청 수신 엔드포인트입니다.

### 5-3. 상품 동기화: `POST /ai/products/sync`

상품이 `ON_SALE` 상태로 변경될 때 자동 호출됩니다.

```json
{
  "artisan": {
    "artisan_id": 1,
    "name": "Dev 공방",
    "certification_level": "ARTISAN",
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

### 5-4. 상품 수정 동기화: `PUT /ai/products/{productId}`

**Request**: 5-3의 `product` 객체와 동일한 구조  
**Response**: 2xx면 성공

### 5-5. 상품 삭제 동기화: `DELETE /ai/products/{productId}`

Request body 없음. Response: 2xx면 성공

---

## 6. 인증 구조 요약

| 경로 패턴 | 인증 방식 | 비고 |
|-----------|-----------|------|
| `/internal/**` | `Authorization: Bearer {BACKEND_AUTH_TOKEN}` | 토큰 미설정 또는 불일치 → **무조건 401** |
| `POST /api/images/presigned-url` | JWT **또는** `Bearer {BACKEND_AUTH_TOKEN}` | AGENT 토큰 사용 시 body에 `memberId` 필수 |
| 그 외 `/api/**` | JWT Bearer Token | 역할(USER/ARTISAN/ADMIN)에 따라 접근 제한 |

**AGENT 역할 접근 가능 경로**

| 경로 | 조건 |
|------|------|
| `POST /internal/generations/{generationId}/completion` | `BACKEND_AUTH_TOKEN` 일치 |
| `POST /api/images/presigned-url` | `BACKEND_AUTH_TOKEN` 일치 + body `memberId` 필수 |

---

## 7. 전체 흐름 빠른 검증

AI 서버 없이 백엔드 단독으로 전체 흐름을 검증합니다.

> **전제**: AI 서버가 없으면 Step 3 후 비동기 job 제출이 실패해 status가 `FAILED`가 됩니다.
> `complete()` 에 상태 가드가 없으므로 이후 콜백(Step 4)을 직접 호출하면 `FAILED → COMPLETED` 전환이 정상 동작합니다.

```bash
BACKEND_AUTH_TOKEN="your-shared-secret-here"

# 1. 서버 실행 확인
curl -s http://localhost:8080/healthz | jq .

# 2. Dev 아티산 + 상품 생성 및 토큰 발급
SETUP=$(curl -s -X POST http://localhost:8080/dev/setup)
echo $SETUP | jq .
ARTISAN_TOKEN=$(echo $SETUP | jq -r '.data.accessToken')
PRODUCT_ID=$(echo $SETUP | jq -r '.data.productId')

# 3. 콘텐츠 생성 요청 — AI 서버 없으면 비동기 job 제출 실패 후 status=FAILED
GENERATION=$(curl -s -X POST "http://localhost:8080/api/content/products/${PRODUCT_ID}/generations" \
  -H "Authorization: Bearer ${ARTISAN_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{"images":["https://cdn.example.com/img1.jpg"],"productName":"청자 다완","howMade":"전통 물레 성형","careTips":"손세척"}')
echo $GENERATION | jq .
GENERATION_ID=$(echo $GENERATION | jq -r '.data.generationId')
# → status: PROCESSING (곧 FAILED로 바뀜, 콜백 검증에는 무관)

# 4. 콜백 직접 호출 — FAILED 상태여도 정상 처리됨
curl -s -X POST "http://localhost:8080/internal/generations/${GENERATION_ID}/completion" \
  -H "Authorization: Bearer ${BACKEND_AUTH_TOKEN}" \
  -H "Idempotency-Key: ${GENERATION_ID}" \
  -F "metadata={\"generationId\":\"${GENERATION_ID}\",\"jobId\":\"job-001\",\"requestId\":\"req-001\",\"idempotencyKey\":\"${GENERATION_ID}\",\"productId\":\"${PRODUCT_ID}\",\"detailPage\":{\"reactDocument\":{\"schemaVersion\":\"2.0\",\"canvasWidth\":774,\"root\":[]}}}" \
  | jq .
# → data.status: "SAVED"

# 5. generation 상태 확인
curl -s "http://localhost:8080/api/content/products/${PRODUCT_ID}/generations/${GENERATION_ID}" \
  -H "Authorization: Bearer ${ARTISAN_TOKEN}" | jq .data.status
# → "COMPLETED"

# 6. 저장된 콘텐츠 확인
curl -s "http://localhost:8080/api/content/products/${PRODUCT_ID}/contents" \
  -H "Authorization: Bearer ${ARTISAN_TOKEN}" | jq .
```

> 실제 AI 서버가 연동되어 있으면 Step 3 직후 status가 `QUEUED`로 바뀌고, AI 서버가 콜백을 보내면 Step 4 없이도 자동으로 `COMPLETED`가 됩니다.

---

## 8. 오류 / 타임아웃 동작

| 상황 | 백엔드 동작 |
|------|------------|
| `BACKEND_AUTH_TOKEN` 미설정 또는 불일치 | `/internal/**` 경로 즉시 **401** |
| AI `/ai/chat` 연결 실패 | 최대 2회 재시도 후 `"현재 AI 추천을 이용할 수 없습니다"` 반환 |
| AI job 제출(`/internal/v1/ai/detail-page-jobs`) 실패 | 최대 2회 재시도 후 generation status → `FAILED` |
| AI `/ai/products/sync` 실패 | 오류 로그 기록 후 무시 (상품 게시 자체는 성공) |
| 콜백 없이 AI 무응답 | status는 `QUEUED` 유지 — 백엔드가 자동 재시도하지 않음. AI 서버가 콜백을 보내야 함 |
| 중복 콜백 (`Idempotency-Key` 동일) | `200 ALREADY_SAVED` 반환 (409 반환 없음) |
| 타임아웃 | 기본 300초 (`AI_TIMEOUT_SECONDS` 환경변수로 조정 가능) |
