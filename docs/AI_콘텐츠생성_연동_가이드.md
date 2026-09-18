# AI 콘텐츠 생성 연동 가이드

AI 서버와 백엔드 간 상세페이지 콘텐츠 생성 연동 방식을 설명합니다.

---

## 1. 전체 흐름

```
[ARTISAN]
  │ 사진 + 상품명 + 제작과정 + 관리법 입력
  ▼
[FE] ─── POST /api/content/products/{productId}/generations ───▶ [BE]
  │                                                                │ ContentGeneration 저장 (status=PROCESSING)
  │                                                                │ 202 즉시 반환
  │                                                                │ @Async로 AI 서버 job 제출
  │                                                                ▼
  │                                                            [AI 서버]
  │                                                                │ 202 QUEUED 즉시 반환
  │                                                                │ (jobId, statusUrl 포함)
  │                                                             BE: status=QUEUED
  │                                                                │
  │                                                                │ 생성 완료 후 AI→BE 콜백
  │                                                                ▼
  │                                            POST /internal/generations/{generationId}/completion
  │                                                                │
  │                                                             BE: status=COMPLETED, S3 이미지 저장
  │
  └─── GET /api/content/products/{productId}/generations/{id} ─── (FE 폴링)
```

---

## 2. 백엔드 실행

### 사전 조건

| 항목 | 버전 |
|------|------|
| Java | 25 |
| Redis | 8+ |

```bash
docker run -d -p 6379:6379 redis:8
```

### `.env` 파일 설정

```dotenv
AI_CONTENT_URL=http://localhost:8002
AI_INTERNAL_AUTH_TOKEN=your-shared-secret-here
BACKEND_AUTH_TOKEN=your-shared-secret-here
```

| 변수명 | 용도 | 방향 |
|--------|------|------|
| `AI_CONTENT_URL` | 콘텐츠 생성 AI 서버 URL | BE → AI |
| `AI_INTERNAL_AUTH_TOKEN` | BE가 `POST /internal/v1/ai/detail-page-jobs` 호출 시 헤더 값 | BE → AI |
| `BACKEND_AUTH_TOKEN` | AI가 BE `/internal/**` 콜백 시 `Authorization: Bearer` 값 | AI → BE |
| `GENERATION_DEADLINE_SECONDS` | QUEUED 데드라인 초 (기본: `1861`) | 스케줄러 |
| `GENERATION_POLL_SCAN_MILLIS` | 스케줄러 스캔 간격 ms (기본: `60000`) | 스케줄러 |

> `BACKEND_AUTH_TOKEN`이 빈 문자열이면 `/internal/**`는 **무조건 401**입니다.

### 실행

```bash
./gradlew bootRun
```

---

## 3. Step 1 — 생성 요청 (FE → BE)

```bash
ARTISAN_TOKEN="eyJhbGci..."
PRODUCT_ID=1

curl -s -X POST "http://localhost:8080/api/content/products/${PRODUCT_ID}/generations" \
  -H "Authorization: Bearer ${ARTISAN_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{
    "images": ["https://cdn.example.com/img1.jpg"],
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

이후 BE가 비동기로 AI 서버에 job을 제출하고, AI가 202 반환 시 status가 `QUEUED`로 바뀝니다.

---

## 4. Step 2 — BE → AI job 제출 (`POST /internal/v1/ai/detail-page-jobs`)

**헤더**

| 헤더 | 값 |
|------|----|
| `X-AI-Internal-Token` | `AI_INTERNAL_AUTH_TOKEN` 값 |
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

**file: `product_image`** — 첫 번째 이미지 URL에서 fetch한 바이너리

**AI Response (202)**

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

BE는 `job_id`, `request_id`, `status_url`을 `markQueued()`로 저장합니다.

---

## 5. Step 3 — AI → BE 콜백 (`POST /internal/generations/{generationId}/completion`)

생성 완료 후 AI 서버가 BE로 호출합니다.

**인증**: `Authorization: Bearer {BACKEND_AUTH_TOKEN}`  
**멱등성**: `Idempotency-Key` 헤더 필수  
**Content-Type**: `multipart/form-data`

| Part 이름 | 종류 | 설명 |
|-----------|------|------|
| `metadata` | form field (JSON) | 생성 메타데이터 |
| `detail_page_image` | file (optional) | 전체 상세페이지 이미지 |
| `detail_page_section_02`, `03`, ... | file (optional) | 섹션별 이미지 |
| `product_photo_*` | file (optional) | 상품 사진 |

**metadata JSON**

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

중복 요청(동일 `Idempotency-Key`) 시:

```json
{ "data": { "status": "ALREADY_SAVED" } }
```

> `ALREADY_SAVED`는 200으로 반환됩니다. 409 없음.

---

## 6. Step 4 — 상태 폴링 (FE → BE)

```bash
curl -s "http://localhost:8080/api/content/products/${PRODUCT_ID}/generations/${GENERATION_ID}" \
  -H "Authorization: Bearer ${ARTISAN_TOKEN}" | jq .data.status
```

| 상태 | 의미 |
|------|------|
| `PROCESSING` | AI 서버 job 제출 전 |
| `QUEUED` | AI 서버 job 제출 완료, 콜백 대기 중 |
| `COMPLETED` | 콜백 수신 완료, 콘텐츠 저장됨 |
| `FAILED` | AI 서버 오류, job 제출 재시도 소진, 또는 데드라인 초과 |

---

## 7. 데드라인 스케줄러 (안전망)

AI 서버가 콜백을 보내지 않는 경우를 대비한 BE 내부 스케줄러입니다.

```
1분 간격 스캔
  → QUEUED 상태 중 requestedAt < now() - 1861초 인 항목
  → fail() 호출 → status=FAILED
  → warn 로그: generationId, productId, requestedAt
```

- **데드라인**: `requestedAt` 기준 **1861초** (30분 + 1분 버퍼)
- **AI 팀 변경 불필요** — BE 내부 타이머, AI API 호출 없음
- 정상 경로는 AI → BE 콜백. 스케줄러는 보완 역할

**로그 예시**

```
WARN  AI 생성 데드라인 초과 — FAILED 처리 generationId=1 productId=10 requestedAt=2026-09-18T10:00:00
INFO  AI 생성 데드라인 스캔 완료 — 만료 처리 건수=1
```

---

## 8. 이미지 업로드 (AI → BE Presigned URL)

AI 서버가 이미지를 업로드할 때는 Presigned URL을 발급받아 S3에 직접 업로드합니다.

### POST /api/images/presigned-url

**인증**: `Authorization: Bearer {BACKEND_AUTH_TOKEN}` + body에 `memberId` 필수

```bash
curl -s -X POST "http://localhost:8080/api/images/presigned-url" \
  -H "Authorization: Bearer ${BACKEND_AUTH_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{
    "fileName": "product.webp",
    "contentType": "image/webp",
    "purpose": "PRODUCT",
    "sourceWidth": 1200,
    "sourceHeight": 800,
    "variants": ["320w", "640w", "1280w"],
    "memberId": 1
  }' | jq .
```

**Response**

```json
{
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

```bash
curl -X PUT "https://s3.amazonaws.com/..." \
  -H "Content-Type: image/webp" \
  --data-binary @image.webp
```

---

## 9. Dev 토큰 발급 (로컬 전용)

`local` 프로파일에서만 활성화됩니다.

```bash
# 테스트 아티산 + 상품 자동 생성 (멱등)
SETUP=$(curl -s -X POST http://localhost:8080/dev/setup)
ARTISAN_TOKEN=$(echo $SETUP | jq -r '.data.accessToken')
PRODUCT_ID=$(echo $SETUP | jq -r '.data.productId')
```

---

## 10. 전체 흐름 빠른 검증 (AI 서버 없이)

> AI 서버 없으면 Step 2 후 비동기 job 제출이 실패해 status가 `FAILED`가 됩니다.
> 콜백(Step 3)을 직접 호출하면 `FAILED → COMPLETED` 전환이 정상 동작합니다.

```bash
BACKEND_AUTH_TOKEN="your-shared-secret-here"

# 1. 서버 확인
curl -s http://localhost:8080/healthz | jq .

# 2. Dev 아티산 + 토큰 발급
SETUP=$(curl -s -X POST http://localhost:8080/dev/setup)
ARTISAN_TOKEN=$(echo $SETUP | jq -r '.data.accessToken')
PRODUCT_ID=$(echo $SETUP | jq -r '.data.productId')

# 3. 콘텐츠 생성 요청
GENERATION=$(curl -s -X POST "http://localhost:8080/api/content/products/${PRODUCT_ID}/generations" \
  -H "Authorization: Bearer ${ARTISAN_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{"images":["https://cdn.example.com/img1.jpg"],"productName":"청자 다완","howMade":"전통 물레 성형","careTips":"손세척"}')
GENERATION_ID=$(echo $GENERATION | jq -r '.data.generationId')

# 4. 콜백 직접 호출
curl -s -X POST "http://localhost:8080/internal/generations/${GENERATION_ID}/completion" \
  -H "Authorization: Bearer ${BACKEND_AUTH_TOKEN}" \
  -H "Idempotency-Key: ${GENERATION_ID}" \
  -F "metadata={\"generationId\":\"${GENERATION_ID}\",\"jobId\":\"job-001\",\"requestId\":\"req-001\",\"idempotencyKey\":\"${GENERATION_ID}\",\"productId\":\"${PRODUCT_ID}\",\"detailPage\":{\"reactDocument\":{\"schemaVersion\":\"2.0\",\"canvasWidth\":774,\"root\":[]}}}" \
  | jq .

# 5. 상태 확인 → "COMPLETED"
curl -s "http://localhost:8080/api/content/products/${PRODUCT_ID}/generations/${GENERATION_ID}" \
  -H "Authorization: Bearer ${ARTISAN_TOKEN}" | jq .data.status
```

---

## 11. 인증 구조

| 경로 패턴 | 인증 방식 |
|-----------|----------|
| `/internal/**` | `Authorization: Bearer {BACKEND_AUTH_TOKEN}` — 불일치 시 **401** |
| `POST /api/images/presigned-url` | JWT **또는** `Bearer {BACKEND_AUTH_TOKEN}` (AGENT 토큰 사용 시 body `memberId` 필수) |
| `/api/**` | JWT Bearer Token |

---

## 12. 오류 / 타임아웃 동작

| 상황 | 백엔드 동작 |
|------|------------|
| `BACKEND_AUTH_TOKEN` 불일치 | `/internal/**` 즉시 **401** |
| AI job 제출 실패 | 최대 2회 재시도 후 `status=FAILED` |
| 콜백 미수신 (1861초 초과) | 스케줄러가 `status=FAILED` 처리 |
| 중복 콜백 (동일 `Idempotency-Key`) | `200 ALREADY_SAVED` |
| 이미지 presigned URL 리다이렉트 | `followRedirects(NORMAL)` 적용됨 |
