# AI파트 인터페이스 & API 명세서

> **프로젝트:** 스토리 중심 장인 공예 커머스 "미담"
**문서 목적:** AI파트가 정의한 인터페이스·데이터 입출력·API 명세
**작성 주체:** AI파트 (제안) → 백엔드 (검토·조정)
**기준일:** 2026.08.28
>

---

## 1. 전체 구조

소비자와 프론트는 **백엔드 챗봇 API만** 상대한다. AI파트는 백엔드 뒤에서 백엔드하고만 통신한다.

```
[소비자]
   │ 자연어 입력 ("엄마 환갑 선물 5만원 이하, 고급스러운 걸로")
   ▼
[프론트] ── 챗봇 메시지 전송 ──▶ [백엔드]
   ▲                              │ ① 메시지·세션 저장(대화 이력)
   │                              │ ② /ai/chat 호출 (자연어 + 세션 맥락 전달)
   │                              ▼
   │                           [AI파트]
   │                              │ 의도분류 → 임베딩 → 하이브리드 검색 → 랭킹 → 추천이유 생성
   │                              │ { reply, intent, products:[{product_id, reason}] }
   │                              ▼
   │ ◀── 카드 조립 + reply ──── [백엔드]
   │                              (product_id로 상품 상세 조회, reason 결합)
   ▼
[모달 렌더링]

※ 별도 경로 — 상품 등록·수정·삭제 (데이터 동기화)
[백엔드] ──▶ [AI파트]  /ai/products  (우리 DB 저장 후 임베딩)
```

### 책임 경계

| 파트 | 책임 |
| --- | --- |
| 프론트 | 입력 UI, 백엔드 챗봇 API 호출, 추천 결과 모달 렌더링 |
| 백엔드 | 세션·대화이력 저장/조회, AI 호출, **product_id로 상품 상세 조회·카드 조립**, 상품 이벤트를 AI로 동기화 |
| AI파트 | 자연어 이해·검색·랭킹·추천이유 생성, 자체 벡터DB(pgvector) 운영. **결과는 id + reason만 반환** |

---

## 2. 백엔드 챗봇 API와의 접점

백엔드가 이미 보유한 세션 기반 챗봇 API. **세션 관리·대화 이력은 전적으로 백엔드 담당.**

| 메서드 | 경로 | 역할 | AI 연동 |
| --- | --- | --- | --- |
| POST | /api/chatbot/sessions | 세션 생성 | — |
| POST | /api/chatbot/sessions/{sessionId}/messages | 메시지 전송·추천 | **내부에서 `/ai/chat` 호출** |
| GET | /api/chatbot/sessions/{sessionId}/messages | 대화 이력 조회 | — |
| DELETE | /api/chatbot/sessions/{sessionId} | 세션 종료 | — |

> 소비자 메시지 전송(POST messages) 시, 백엔드가 대상·예산·용도·취향 분석을 위해 우리 `/ai/chat`을 호출한다.
>

---

## 3. 데이터 입출력 구조

**들어올 땐 서사 포함 풀데이터, 나갈 땐 id + 이유만.**

### 흐름 ① 상품 등록·동기화 (백엔드 → AI)

```
백엔드 보유 상품/장인 원본
  → POST /ai/products (풀데이터: 서사 포함)
  → AI DB 저장 → embedding_text 조립 → KURE-v1 임베딩
```

### 흐름 ② 추천 (AI → 백엔드)

```
소비자 자연어 메시지 (+ 세션 맥락)
  → POST /ai/chat
  → [AI] 의도분류로 조건 추출 (가격·용도·취향) → 검색·랭킹
  → { reply, intent, extracted, products:[{product_id, reason}] }  ← 경량
```

> 필터 조건은 백엔드가 주지 않는다. AI가 자연어에서 직접 추출한다.
>

> 결과 표시에 필요한 상품 상세(이미지·색상·후기·배송 등)는 백엔드가 이미 보유하므로 AI가 반환하지 않는다. AI는 `product_id`와 `reason`만 넘기고, 백엔드가 id로 상세를 조회해 카드를 조립한다.
>

---

## 4. API 명세

Base Path: `/ai`
공통 응답 규약: 추천 응답은 항상 `{ reply, intent, products[] }` 구조를 유지한다. 추천 상품이 없어도 `reply`는 반드시 존재하며 `products`는 빈 배열.

---

### 4-1. POST /ai/chat — 추천

백엔드의 `POST /api/chatbot/sessions/{sessionId}/messages`가 내부에서 호출한다.

**Request (백엔드 → AI)**

```json
{
  "session_id": "abc123",                         // 필수. 백엔드 세션 ID
  "message": "엄마 환갑 선물인데 고급스러운 걸로 5만원대",  // 필수. 소비자 원문 (가공 없이 그대로)
  "history": [                                    // 선택(optional). 최근 N턴만 (권장 5~6턴)
    { "role": "user", "content": "이전 질문..." },
    { "role": "assistant", "content": "이전 응답..." }
  ]
}
```

> **백엔드는 필터 조건을 넘기지 않는다.** 백엔드는 소비자 자연어를 파싱하지 않으므로 사전에 아는 구조적 조건이 없다. "5만원대 → max_price:50000", "환갑 선물 → purpose:[선물,환갑]" 같은 조건 추출은 **AI 의도분류(①, Qwen3)의 역할**이다. 백엔드는 원문(`message`)만 전달하고, 조건 인지·추출은 전적으로 AI가 수행한다.
>

**Response (AI → 백엔드)**

```json
{
  "reply": "고급스러운 환갑 선물이라면 오래 두고 쓰실 작품이 좋겠어요...",  // 항상 존재
  "intent": "gift_recommendation",                                     // 의도분류 결과
  "products": [                                                        // 없으면 []
    { "product_id": 1, "reason": "사흘을 구워 같은 빛이 없는 다완이라 어머님 선물로 어울립니다." },
    { "product_id": 7, "reason": "..." }
  ]
}
```

> `extracted`는 AI가 원문에서 파악한 조건을 그대로 노출한다. **의도 파악이 제대로 됐는지 검증·디버깅**하는 용도이며, 프론트에서 "5만원 이하 · 선물 조건으로 찾았어요" 같은 안내에도 활용할 수 있다. 정량 조건(가격 등)은 하드 필터로, 취향(`vibe`)은 벡터 검색으로 반영된다.
>

**필드 설명**

| 필드 | 필수 | 설명 |
| --- | --- | --- |
| session_id | ✅ | 백엔드 세션 식별자 |
| message | ✅ | 소비자 자연어 원문. **백엔드가 임의 가공 금지** (의도분류 정확도 보호) |
| history | ⬜ | 이전 대화. 멀티턴 맥락 반영용. 없으면 단발 추천. **최근 5~6턴 상한** |
| reply | (응답) | 대화형 추천 코멘트. 항상 존재 |
| intent | (응답) | 분류된 의도 (예: gift_recommendation, product_search 등) |
| extracted | (응답) | AI가 자연어에서 추출한 조건(가격·용도·취향 등). 의도 파악 검증·프론트 안내용 |
| products | (응답) | 추천 상품 배열. `product_id` + `reason`. 없으면 `[]` |

> **조건 추출은 AI의 책임.** 요청에는 `filters`가 없다. 백엔드는 원문만 넘기고, AI가 `message`를 분석해 `extracted`를 생성한다. 이는 "LLM은 언어처리(의도분류·임베딩·생성)에만, 검색·랭킹은 계산"이라는 설계 원칙과 일치한다 — 의도분류가 언어를 구조로 바꾸고, 그 구조로 계산 검색을 수행한다.
>

---

### 4-2. POST /ai/products — 상품 등록 (저장 후 임베딩)

**저장 → embedding_text 조립 → 임베딩** 순으로 처리. 원본을 먼저 적재하므로 임베딩 실패·모델 교체 시 재임베딩만 재실행 가능.

/api/products

**Request (백엔드 → AI)**

```json
{
  "artisan": {
    "artisan_id": 10,                    // 필수. 장인 발급 ID
    "name": "김도공방",                   // 필수
    "certification_level": "보유자",      // 보유자/전승교육사/이수자/일반
    "introduction": "3대째 이천에서 청자를 굽습니다...",  // 장인 서사 (임베딩 결합용)
  },
  "product": {
    "product_id": 1,                     // 필수. 상품 발급 ID
    "title": "청자 상감 다완",            // 필수
    "category": "POTTERY",               // 필터
    "material": "청자토",
    "price": 85000,                      // 필터
    "purpose": ["선물", "다도"],          // 용도·대상·상황 태그 (필터·부스팅)
    "making_story": "물레로 형태를 잡은 뒤...",   // ★서사 — 어떻게 만들어지는지?
    "usage_care": "차를 우린 뒤 미지근한 물로...", // ★서사 — 관리법
    "production_period_days": 14         // 희소성 부스팅(제작기간)
  }
}
```

**Response**

```json
{ "success": true, "product_id": 1, "embedded": true }
```

> **artisan은 upsert 처리** (한 장인이 여러 상품을 올려도 장인 정보 중복 저장 방지).
>

---

### 4-3. PUT /ai/products/{product_id} — 상품 수정 (재임베딩)

**Request** — 변경된 필드만 전달. `making_story`·`usage_care`·`title`·`material` 등 임베딩 대상 필드가 바뀌면 재임베딩을 트리거한다.

```json
{
  "product": {
    "making_story": "수정된 제작 이야기...",
    "price": 79000
  }
}
```

**Response**

```json
{ "success": true, "product_id": 1, "re_embedded": true }
```

> `re_embedded`는 임베딩 대상 필드가 변경되어 재임베딩이 수행됐는지 여부. 가격만 바뀐 경우 `false`일 수 있다.
>

---

### 4-4. DELETE /ai/products/{product_id} — 상품 삭제

**Response**

```json
{ "success": true, "product_id": 1, "deleted": true }
```

---

### 4-5. GET /ai/health — 상태 확인

**Response**

```json
{
  "status": "ok",
  "db": "connected",
  "embedder": "loaded"
}
```

---

## 5. 벡터 DB 스키마

손으로 채우는 값과 파이프라인 자동 생성 값을 구분한다.

### artisans (장인)

```sql
CREATE TABLE artisans (
  artisan_id           BIGINT PRIMARY KEY,   -- 백엔드 발급 ID
  name                 TEXT NOT NULL,
  certification_level  TEXT,                 -- 보유자/전승교육사/이수자/일반 (랭킹 가중)
  introduction         TEXT,                 -- 장인 서사 (임베딩 결합용)
  craft                TEXT,
  region               TEXT                  -- 선택
);
```

### products (상품)

```sql
CREATE TABLE products (
  product_id             BIGINT PRIMARY KEY,   -- 백엔드 발급 ID
  artisan_id             BIGINT REFERENCES artisans(artisan_id),

  -- ── 백엔드가 전달하는 값 ──
  title                  TEXT NOT NULL,
  category               TEXT,                 -- 필터
  material               TEXT,
  price                  INTEGER,              -- 필터
  purpose                TEXT[],               -- 용도·대상·상황 태그
  making_story           TEXT,                 -- 서사(어떻게 만들어 졌나요)
  usage_care             TEXT,                 -- 서사(관리법)
  production_period_days INTEGER,              -- 희소성 부스팅

  -- ── 파이프라인 자동 생성 ──
  embedding_text         TEXT,                 -- 아래 조립 규칙 참고
  embedding              VECTOR(1024),         -- KURE-v1
  search_text            TEXT,                 -- BM25용 형태소 텍스트
  evidence               JSONB,                -- 환각 방어 근거

  -- ── 운영 ──
  is_test                BOOLEAN DEFAULT true  -- 목데이터 격리
);

CREATE INDEX ON products USING hnsw (embedding vector_cosine_ops);
```

### 파생 필드 생성 규칙

```
embedding_text = title + " " + material + " " + making_story
                 + " " + usage_care + " " + artisan.introduction

search_text    = embedding_text 를 형태소 분석(mecab/Kiwi) → BM25 색인

evidence = {
  "artisan_input": making_story + usage_care 원문,   // 최고 신뢰 근거
  "verified":      certification_level 값,             // 공인 등급
  "ai_inference":  null                                // AI 표현은 구분 표시
}
```

### 검색·랭킹에서의 필드 역할

| 단계 | 사용 필드 |
| --- | --- |
| 벡터 검색 | embedding (← embedding_text) |
| 키워드 검색 | search_text (BM25) |
| 하드 필터 | category, price, purpose |
| 랭킹 가중 | certification_level(등급), production_period_days, purpose |
| 추천이유 생성 | evidence (환각 방어) |

> 등급 가중치: 보유자 +0.30 / 전승교육사 +0.20 / 이수자 +0.10 (유사도 점수에 가산)
>

---

## 6. 백엔드에 요청하는 사항

명세 준수와 별개로, 데이터 정합성을 위해 백엔드가 지켜줘야 할 항목.

1. **`/ai/chat` 호출 시 소비자 원문을 그대로 전달** — 임의 요약·가공 시 의도분류가 흔들린다.
2. **상품 생성·수정·삭제 이벤트를 빠짐없이 `/ai/products` 계열로 전달** — 누락 시 검색DB와 실제 상품 목록이 어긋난다.
3. **history는 최근 5~6턴 상한으로 전달** — 전체 이력을 넘기면 프롬프트가 무거워지고 처리 지연·비용 증가.
4. **certification_level·purpose 등 코드값 체계 합의** — enum 값 목록을 양측이 공유해 표기 불일치를 방지한다.

---

## 부록. 예상 대화 흐름 (멀티턴 예시)

```
소비자: "엄마 환갑 선물 5만원대"
  → AI: 추천 A, B, C + reply
소비자: "좀 더 고급스러운 걸로"          ← history 필요 (앞 맥락 기준)
  → AI: 재추천 (고급 라인 부스팅)
소비자: "그 중에 파란색 있어?"            ← history 필요
  → AI: 색상 조건 반영 필터링
```

> 뒤 두 질문은 앞 맥락 없이는 해석 불가 → history optional 설계의 근거.
>

---