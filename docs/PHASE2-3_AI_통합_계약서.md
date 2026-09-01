# [AI ↔ BE ↔ FE] 통합 계약서 — PHASE 2-3

---

### 1. 문서 정보

| 버전 | 일자 | 변경 내용 | 변경 사유 | 영향 범위 | 작성자 |
| --- | --- | --- | --- | --- | --- |
| v0.1 | 2026-09-01 | 초안 작성 | 최초 작성 | - | AI / BE / FE |

---

### 2. 개요

| 항목 | 내용 |
| --- | --- |
| 협업 직군 | AI, BE, FE |
| 목적 | AI 기능의 호출·입력·출력·오류 처리 기준을 정의하여 직군 간 통합 기준 확정 |
| 적용 범위 | AI 추천 챗봇 / AI 상세페이지 제작 |

---

### 3. AI 공통 연동 기준

| No. | 정의 항목 | 합의 내용 |
| --- | --- | --- |
| 3-1 | 호출 구조 | FE → BE → AI (단방향). FE는 BE 챗봇 API만 호출. AI는 BE에서만 호출받음 |
| 3-2 | 인증 방식 | BE ↔ AI 간 내부 서비스 통신 (Internal). FE는 JWT Bearer Token으로 BE 인증 |
| 3-3 | Request Format | `Content-Type: application/json`, HTTP/JSON |
| 3-4 | Response Format | `{ "success": true/false, "data": { ... } }` — BE 공통 포맷 준수. AI 내부 응답은 AI 자체 포맷 |
| 3-5 | Timeout 기준 | 챗봇: 30초 / 상세페이지 생성(비동기): 별도 폴링 |
| 3-6 | Retry 기준 | AI 호출 실패 시 BE에서 최대 2회 재시도, 최종 실패 시 Fallback 응답 반환 |
| 3-7 | Error Format | `{ "success": false, "status": 5xx, "errorCode": "AI_UNAVAILABLE" }` |
| 3-8 | Fallback 기준 | AI 서버 응답 없음 → BE가 "현재 AI 추천을 이용할 수 없습니다" 안내 메시지 반환. 빈 추천 결과(products: [])는 에러 아님 |
| 3-9 | Logging 기준 | BE는 AI 호출 요청/응답/소요시간/에러를 모두 로깅. AI는 내부 파이프라인 단계별 로깅 |

---

### 4. AI 추천 챗봇

#### 4-1. 전체 흐름

```
[소비자]
   │ 자연어 입력 ("엄마 환갑 선물 고급스러운 걸로 5만원대")
   ▼
[FE] ─── POST /api/chatbot/sessions/{sessionId}/messages ───▶ [BE]
   ▲                                                            │ ① 메시지·세션 저장(대화 이력)
   │                                                            │ ② POST /ai/chat 호출 (원문 + history 전달)
   │                                                            ▼
   │                                                         [AI 서버 — FastAPI]
   │                                                            │ ① 의도분류·조건추출 (Qwen3)
   │                                                            │ ② 쿼리 임베딩 (BGE-M3 / KURE-v1)
   │                                                            │ ③ 하이브리드 검색 (Vector + BM25 + RRF)
   │                                                            │ ④ 인증 등급 가중 랭킹 (최대 3개)
   │                                                            │ ⑤ 근거 기반 추천이유 생성 (Qwen3)
   │                                                            ▼
   │ ◀── 카드 조립(product_id로 상품 상세 조회 + reason 결합) ── [BE]
   ▼
[모달 렌더링]
```

#### 4-2. 세부 합의 내용

| No. | 정의 항목 | 합의 내용 |
| --- | --- | --- |
| 4-1 | 호출 방식 | BE → AI 단방향. `POST /ai/chat` |
| 4-2 | 사용자 입력 형식 | 소비자 자연어 원문 — BE는 **가공 없이** 그대로 전달 (임의 변환 시 의도분류 흔들림) |
| 4-3 | 입력 데이터 | `session_id`, `message` (소비자 원문), `history` (최근 5~6턴, optional) |
| 4-4 | 출력 데이터 | `reply` (항상 존재), `intent`, `extracted` (조건 추출 결과), `products` (product_id + reason 목록, 없으면 `[]`) |
| 4-5 | 스트리밍 응답 여부 | 미적용 (단일 JSON 응답) — 향후 검토 |
| 4-6 | 세션 유지 방식 | BE가 세션 생성 및 대화 이력 관리. AI에는 매 요청마다 `session_id` + `history` 동봉 |
| 4-7 | 대화 이력 저장 여부 | BE DB에 저장. AI에 전달하는 history 상한은 **최근 5~6턴** |
| 4-8 | 응답 지연 허용 범위 | 30초 이내. 초과 시 Timeout 처리 후 Fallback 응답 |
| 4-9 | 추천 상품 전달 방식 | AI → BE: `{ product_id, reason }` 배열. BE가 product_id로 상품 상세 조회 후 카드 조립 → FE 반환 |
| 4-10 | AI 응답 실패 처리 | BE가 Fallback 메시지 반환. FE는 에러 모달 없이 안내 문구 표시 |

#### 4-3. BE → AI Request (POST /ai/chat)

```json
{
  "session_id": "abc123",
  "message": "엄마 환갑 선물 고급스러운 걸로 5만원대",
  "history": [
    { "role": "user", "content": "이전 메시지" },
    { "role": "assistant", "content": "이전 응답" }
  ]
}
```

#### 4-4. AI → BE Response

```json
{
  "reply": "환갑 선물로 딱 맞는 청자 다완을 찾았어요.",
  "intent": "gift_recommendation",
  "extracted": {
    "maxPrice": 50000,
    "purpose": ["선물", "환갑"],
    "vibe": "고급스러운"
  },
  "products": [
    { "product_id": 1, "reason": "보유자가 직접 빚은 청자로, 60년 경력의 기법이 담겨 있습니다." },
    { "product_id": 2, "reason": "..." }
  ]
}
```

> `products`가 `[]`이면 추천 결과 없음 — 에러가 아닌 정상 응답. BE는 `reply`의 안내 문구를 FE에 그대로 전달한다.

---

### 5. AI 상세페이지 제작

#### 5-1. 전체 흐름

```
[ARTISAN(판매자 FE)]
   │ 사진(3~12장) + 작품명 + 어떻게 만드셨나요 + 관리법 입력
   ▼
[FE] ─── POST /api/content/products/{productId}/generations ───▶ [BE]
   │                                                               │ 취재 데이터(making_story, usage_care) 저장
   │                                                               │ AI 생성 요청 (비동기)
   │                                                               ▼
   │                                                            [AI 서버]
   │                                                               │ 템플릿 기반 상세페이지 문구 생성 (Qwen3)
   │                                                               │ 블록 구조(h2/p/img) 조립 html 생성
   │                                                               ▼
   │ ◀── GET /api/content/products/{productId}/generations/{id} ── [BE] (폴링)
   |         백엔드에서 html 를 분해하여 json 형태로 쪼개어 프론트에게 전달 
   ▼
[초안 편집 화면 렌더링]
```

#### 5-2. 세부 합의 내용

| No. | 정의 항목 | 합의 내용 |
| --- | --- | --- |
| 5-1 | 호출 방식 | BE → AI 비동기. `POST /ai/products` (상품 게시 시점에 making_story·usage_care 포함 동기화) |
| 5-2 | 입력 데이터 | `images` (imageId 목록, 3~12장), `productName`, `howMade` (제작과정), `careTips` (관리법) |
| 5-3 | 출력 데이터 | 블록 구조 배열 — `{ order, tag(h2/p/img/video), text, imageUrl }` |
| 5-4 | 이미지 생성 여부 | 이미지 직접 생성 없음 — 장인이 업로드한 사진을 블록에 배치 |
| 5-5 | 이미지 전달 방식 | BE S3에 저장된 imageId를 AI에 전달. AI는 imageUrl을 블록에 매핑 |
| 5-6 | 생성 완료 기준 | `status: COMPLETED` — FE는 `GET /api/content/products/{productId}/generations/{generationId}` 폴링으로 확인 |
| 5-7 | 응답 지연 허용 범위 | 비동기 처리 — 단일 응답 타임아웃 없음. 생성 완료까지 최대 대기 기준은 추후 합의 |
| 5-8 | 생성 실패 기준 | `status: FAILED` — FE에 실패 안내 및 재시도 CTA 표시 |
| 5-9 | 재생성 방식 | `POST /api/content/products/{productId}/generations` 재호출 (동일 엔드포인트) |
| 5-10 | 생성 결과 수정 방식 | 생성된 블록을 ARTISAN이 직접 편집 — `PATCH /api/content/products/{productId}/contents/{contentId}` |

#### 5-3. 상품 게시 시점 AI 동기화 (BE → AI)

상품 게시(`POST /api/content/products/{productId}/publish`) 성공 시 BE가 AI에 아래 데이터를 동기화한다.

```json
{
  "artisan": {
    "artisan_id": 10,                    // 필수. 장인 발급 ID
    "name": "김도공방",                   // 필수
    "certification_level": "보유자",      // 보유자/전승교육사/이수자/일반
    "introduction": "3대째 이천에서 청자를 굽습니다..."  // 장인 서사 (임베딩 결합용)
  },
  "product": {
    "product_id": 1,                     // 필수. 상품 발급 ID
    "title": "청자 상감 다완",            // 필수
    "category": "POTTERY",               // 필터
    "material": "청자토",
    "price": 85000,                      // 필터
    "gift_theme": ["BIRTHDAY_60TH"],     // 선물 테마 (하드 필터)
    "purpose_tags": ["다도"],            // 자유 태그: 용도 (벡터·태그)
    "making_story": "물레로 형태를 잡은 뒤...",   // ★서사 — 어떻게 만들어지는지?
    "usage_care": "차를 우린 뒤 미지근한 물로...", // ★서사 — 관리법
    "production_period_days": 14,        // 희소성 부스팅(제작기간)
    "color": ["BLUE"]                    // enum, 하드 필터 (WHITE/BLACK/GRAY/RED/BLUE/GREEN/BROWN)
  }
}
```

> `making_story`·`usage_care`는 AI 추천 품질을 직접 좌우하는 핵심 서사 데이터다. 누락 시 벡터 검색 정확도 저하.

#### 5-4. AI 동기화 이벤트 목록

| 이벤트 | BE API | AI 엔드포인트 | 누락 시 영향 |
| --- | --- | --- | --- |
| 상품 게시(등록) | `POST /api/content/products/{productId}/publish` | `POST /ai/products` | AI 추천에 해당 상품 미노출 |
| 상품 수정 | `PATCH /api/products/{productId}` | `PUT /ai/products/{id}` | AI DB와 원본 불일치 |
| 상품 삭제 | `DELETE /api/products/{productId}` | `DELETE /ai/products/{id}` | 삭제된 상품이 계속 추천됨 |

---

### 6. AI 모델 및 운영 기준

| No. | 정의 항목 | 합의 내용 |
| --- | --- | --- |
| 6-1 | 사용 모델 | LLM: Qwen3 (Ollama) / 임베딩: BGE-M3 (KURE-v1 병행 평가 후 확정) / 리랭커: bge-reranker-v2-m3-ko (선택적) |
| 6-2 | 모델 버전 관리 | 목데이터 평가 후 최종 확정. 변경 시 BE·FE에 사전 공지 필수 |
| 6-3 | Prompt 관리 방식 | AI 서버 내부 관리. 시스템 프롬프트는 외부에 노출하지 않음 |
| 6-4 | 모델 변경 기준 | 추천 정확도 미달 또는 응답 품질 기준 미달 시 — 목데이터 평가 기준으로 판단 |
| 6-5 | AI 응답 품질 기준 | 추천이유는 evidence(making_story·usage_care·certification_level) 기반만 허용. 없는 사실 생성 금지 (환각 방어) |
| 6-6 | 사용량 제한 | 완전 로컬 운영 (MacBook M4 24GB, Ollama). 외부 API 미사용 → 과금 없음 |
| 6-7 | 비용 관리 기준 | 로컬 LLM 서빙으로 API 비용 없음. 인프라 비용은 AI 자체 서버 운영 비용에 포함 |

#### 6-8. 인증 등급 가중치

| 인증 등급 | 가중치 |
| --- | --- |
| 보유자 | +0.30 |
| 전승교육사 | +0.20 |
| 이수자 | +0.10 |
| 일반 | 없음 |

> 후기·판매량이 없는 신규 장인도 노출 가능하게 하는 콜드스타트 해결 전략.

#### 6-9. 안전성 정책 요약

| 정책 | 기준 |
| --- | --- |
| 환각 방어 | 추천이유 생성 시 evidence 필드 외 사실 주장 금지 |
| 억지 매칭 금지 | 유사도 임계값(τ) 미만 상품은 추천 결과에서 제외. 0건 결과는 에러 아닌 정상 응답 |
| 프롬프트 인젝션 방어 | 사용자 입력은 데이터로만 취급. 입력 내 지시문은 시스템 정책을 덮어쓰지 못함 |
| AI 생성 사실 표시 | AI기본법 제31조(2026.07.21 시행) — AI 생성 콘텐츠에 생성 사실 표시 필수 |
| 라이선스 | AI DB 서사 데이터는 장인 직접 입력 원문만 사용. 공공누리 제4유형 외부 데이터 사용 금지 |
