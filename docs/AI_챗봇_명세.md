# AI파트 인터페이스 & API 명세서 v2

> **프로젝트:** 스토리 중심 장인 공예 커머스 "미담"
> **문서 목적:** AI파트가 정의한 인터페이스·데이터 입출력·API 명세
> **작성 주체:** AI파트 (제안) → 백엔드 (검토·조정)
> **기준일:** 2026.09.03 (실데이터 구조 반영)

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
   │                              │ 의도분류 → 임베딩 → 하이브리드 검색 → 랭킹 → 추천이유·후속칩 생성
   │                              │ { reply, intent, product_ids: [78, 816, 52], suggestions }
   │                              │ (2026-09-14: products[].reason 제거, product_ids 정수 배열로 통합)
   │                              ▼
   │ ◀── 카드 조립 + reply ──── [백엔드]
   │                              (product_id로 상품 상세 조회, 공유 reply를 카드 위에 표시)
   ▼
[모달 렌더링]

※ 별도 경로 ① — 상품 등록·수정·삭제 (데이터 동기화)
[백엔드] ──▶ [AI파트]  POST /ai/products  (우리 DB 저장 후 임베딩)

※ 별도 경로 ② — 상세페이지 콘텐츠 AI 생성
[장인] ──▶ [백엔드]  POST /api/content/products/{productId}/generations  (202 즉시 반환)
              └─ @Async ──▶ [AI파트]  POST /ai/products  {generationId, images, howMade, careTips, ...}
                               AI가 블록 JSON 배열 반환
              └─ generation.complete(blocks) → status=COMPLETED
[장인] ──▶ [백엔드]  GET /api/content/products/{productId}/generations/{id}  (폴링)
```

### 책임 경계

| 파트 | 책임 |
|------|------|
| 프론트 | 입력 UI, 백엔드 챗봇 API 호출, 추천 결과 모달 렌더링 |
| 백엔드 | 세션·대화이력 저장/조회, AI 호출, **product_id로 상품 상세 조회·카드 조립**, 상품 이벤트를 AI로 동기화 |
| AI파트 | 자연어 이해·검색·랭킹·추천이유·후속칩 생성, 자체 벡터DB(pgvector) 운영. **결과는 product_ids 배열 + 공유 reply만 반환** (2026-09-14: 상품별 reason 필드 제거됨) |

---

## 2. 백엔드 챗봇 API와의 접점

백엔드가 이미 보유한 세션 기반 챗봇 API. **세션 관리·대화 이력은 전적으로 백엔드 담당.**

| 메서드 | 경로 | 역할 | AI 연동 |
|--------|------|------|---------|
| POST | /api/chatbot/sessions | 세션 생성 | — |
| POST | /api/chatbot/sessions/{sessionId}/messages | 메시지 전송·추천 | **내부에서 `/ai/chat` 호출** |
| GET | /api/chatbot/sessions/{sessionId}/messages | 대화 이력 조회 | — |
| DELETE | /api/chatbot/sessions/{sessionId} | 세션 종료 | — |

> 소비자 메시지 전송(POST messages) 시, 백엔드가 대상·예산·용도·취향 분석을 위해 `/ai/chat`을 호출한다.

---

## 3. 데이터 입출력 구조

**들어올 땐 서사 포함 풀데이터, 나갈 땐 id + 공유 reply만.**

### 흐름 ① 상품 등록·동기화 (백엔드 → AI)

```
백엔드 보유 상품/장인 원본
  → POST /ai/products (풀데이터: 서사 포함)
  → AI DB 저장 → embedding_text 조립 → BGE-M3 임베딩
```

> **개발 단계에서는** 백엔드가 제공한 CSV(product/artisan/category/subcategory)를 적재 스크립트로 벌크 적재한다. **실서비스에서는** 상품 등록·수정·삭제마다 백엔드가 `/ai/products` 계열 API를 호출한다. (두 경로 모두 같은 스키마로 수렴)

### 흐름 ② 추천 (AI → 백엔드)

```
소비자 자연어 메시지 (+ 세션 맥락)
  → POST /ai/chat
  → [AI] 의도분류로 조건 추출 (가격·선물테마·색상·취향) → 검색·랭킹
  → { reply, intent, product_ids: [int], suggestions }  ← 경량(2026-09-14: reason 필드 없음, reply 하나를 공유)
```

> 필터 조건은 백엔드가 주지 않는다. AI가 자연어에서 직접 추출한다.
> 결과 표시에 필요한 상품 상세(이미지·색상·후기·배송 등)는 백엔드가 이미 보유하므로 AI가 반환하지 않는다.
> AI는 `product_ids` 배열과 상품들을 아우르는 공유 `reply` 하나만 넘기고, 백엔드가 id로 상세를 조회해 카드를 조립한다.
> (2026-09-14: 상품마다 다른 reason은 만들지 않음 — 공유 reply가 특정 상품 이름을 콕 집어 말하는 문장을 포함하면, 그대로 복사 시 다른 상품 카드에 엉뚱한 문장이 들어가는 문제가 생겨 reason 필드는 제거됨)

---

## 4. API 명세

**Base Path:** `/ai`

**공통 응답 규약:** 추천 응답은 항상 `{ reply, intent, product_ids[], suggestions[] }` 구조를 유지한다. 추천 상품이 없어도 `reply`는 반드시 존재하며 `product_ids`는 빈 배열.

---

### 4-1. POST /ai/chat — 추천

백엔드의 `POST /api/chatbot/sessions/{sessionId}/messages`가 내부에서 호출한다.

**Request (백엔드 → AI)**

```json
{
  "session_id": "abc123",
  "message": "엄마 환갑 선물인데 고급스러운 걸로 5만원대",
  "history": [
    { "sender": "USER", "content": "이전 질문..." },
    { "sender": "ADMIN", "content": "이전 응답..." }
  ]
}
```

> **(2026-09-14 갱신) history 항목은 `{role, content}`가 아니라 백엔드 "챗봇 대화 이력 조회" API가 실제로 쓰는 `{sender, content}` 형식이다.** `sender`는 `"USER"`(소비자 발화) 또는 그 외 값(`"ARTISAN"`·`"ADMIN"` 등, 챗봇 발화로 취급)이다. AI파트 내부에서 `sender=="USER"`만 user로, 나머지는 전부 assistant로 변환해 쓴다(`app/main.py:_to_pipeline_history`). 이전 버전 문서는 `{role, content}`로 표기돼 있었는데 실제 코드와 달라 이번에 정정함 — 백엔드가 옛 표기대로 `role` 필드로 보내면 AI파트가 `sender` 키를 못 찾아 모든 이전 발화를 챗봇 것으로 잘못 인식한다.

> **백엔드는 필터 조건을 넘기지 않는다.** 백엔드는 소비자 자연어를 파싱하지 않으므로 사전에 아는 구조적 조건이 없다. "5만원대 → max_price:50000", "환갑 선물 → gift_theme:[BIRTHDAY_60TH]" 같은 조건 추출은 **AI 의도분류(Qwen3)의 역할**이다. 백엔드는 원문(`message`)만 전달한다.

**Response (AI → 백엔드)**

```json
{
  "reply": "고급스러운 환갑 선물이라면 오래 두고 쓰실 작품이 좋겠어요...",
  "intent": "gift_recommendation",
  "product_ids": [78, 816, 52],
  "suggestions": ["3만 원 아래로", "다른 종류로", "포장되는 것만"]
}
```

**필드 설명**

| 필드 | 필수 | 설명 |
|------|------|------|
| session_id | ✅ | 백엔드 세션 식별자 |
| message | ✅ | 소비자 자연어 원문. **백엔드가 임의 가공 금지** (의도분류 정확도 보호) |
| history | ⬜ | 이전 대화. 멀티턴 맥락 반영용. 없으면 단발 추천. **최근 5~6턴 상한** |
| reply | (응답) | 대화형 추천 코멘트. 항상 존재 |
| intent | (응답) | 분류된 의도 (gift_recommendation, product_search 등) |
| product_ids | (응답) | 추천 상품 배열. 최대 3개. 없으면 `[]`. 왼쪽부터 1, 2, 3순위 |
| suggestions | (응답) | 후속 제안 칩 (AI 생성). 최대 3개. 프론트가 버튼으로 렌더링 |

> **조건 추출은 AI의 책임.** 요청에는 `filters`가 없다. 백엔드는 원문만 넘기고, AI가 `message`를 분석해 검색 조건을 만든다.

**추천 정책 (비즈니스 규칙)**

- **최대 3개.** 큐레이션 성격. 하한 없음 — 유사도 컷을 넘은 만큼만 반환(억지로 채우지 않음)
- **유사도 컷(τ) 미만은 제외.** 값은 목데이터 평가 후 확정
- **결과 0개 → 재질문 유도.** product_ids 빈 배열 + 안내 reply + 조건 넓히는 suggestions. 대체 상품을 억지로 추천하지 않는다

---

### 4-2. POST /ai/products (콘텐츠 생성) — 상세페이지 블록 생성

백엔드의 `POST /api/content/products/{productId}/generations`가 내부에서 비동기로 호출한다.

**호출 시점:** 장인이 AI 상세페이지 생성을 요청하면 백엔드가 즉시 202를 반환하고, 별도 스레드(@Async)에서 이 API를 동기 HTTP로 호출한다.

**Request (백엔드 → AI)**

```json
{
  "generationId": 1,
  "productId": 10,
  "images": ["imageId1", "imageId2"],
  "productName": "청자 다완",
  "howMade": "물레로 형태를 잡은 뒤 손으로 빚음",
  "careTips": "차를 우린 뒤 미지근한 물로 헹군다"
}
```

| 필드 | 필수 | 설명 |
|------|------|------|
| generationId | ✅ | 백엔드 발급 생성 요청 ID (content_generation.id) |
| productId | ✅ | 상품 ID |
| images | ✅ | S3 이미지 ID 목록 (1개 이상) |
| productName | ✅ | 상품명 |
| howMade | ✅ | 제작 과정 (취재 데이터 기반) |
| careTips | ✅ | 관리 방법 (취재 데이터 기반) |

**Response (AI → 백엔드)**

블록 JSON 배열 문자열을 직접 반환한다 (`Content-Type: application/json`).

```json
[
  { "order": 1, "tag": "h2", "text": "청자 다완의 이야기" },
  { "order": 2, "tag": "p",  "text": "물레 위에서 손으로 빚어낸..." },
  { "order": 3, "tag": "img", "imageUrl": "https://cdn.example.com/img1.jpg" }
]
```

| 필드 | 설명 |
|------|------|
| order | 블록 순서 (1부터 시작) |
| tag | 블록 유형: `h2` (제목), `p` (본문), `img` (이미지) |
| text | 텍스트 내용 (`h2`, `p` 블록) |
| imageUrl | 이미지 URL (`img` 블록) |

**백엔드 처리 흐름**

```
POST /api/content/products/{productId}/generations  (장인 요청)
  → content_generation 저장 (status=PROCESSING)
  → 202 Accepted 즉시 반환
  → @Async 별도 스레드:
      POST /ai/products (AI 호출, 동기 HTTP, 타임아웃 30초)
      성공: generation.complete(blocks) → status=COMPLETED, generated_blocks 저장
      실패: generation.fail() → status=FAILED, log.error

GET /api/content/products/{productId}/generations/{generationId}  (프론트 폴링)
  → status 반환 (PROCESSING | COMPLETED | FAILED)
```

**실패 처리**

- AI 호출 실패 또는 타임아웃(30초) → `status=FAILED`로 영속, 예외 삼키지 않고 `log.error` 기록
- 프론트는 COMPLETED/FAILED 상태를 받을 때까지 폴링 지속

---

### 4-3. POST /ai/products — 상품 등록 (저장 후 임베딩)

**저장 → embedding_text 조립 → 임베딩** 순으로 처리.

> **개발 단계**에서는 이 API 대신 CSV 벌크 적재를 사용한다. 이 API는 **실서비스에서 상품이 하나씩 등록·동기화되는 경로**를 정의한다.

**Request (백엔드 → AI)**

```json
{
  "artisan": {
    "artisan_id": 10,
    "business_name": "김도공방",
    "certification_level": "NATIONAL_INTANGIBLE_HERITAGE",
    "region": "경기 이천"
  },
  "product": {
    "product_id": 1,
    "name": "청자 상감 다완",
    "category_code": "POTTERY",
    "subcategory_code": "다완",
    "material": "청자토",
    "price": 85000,
    "color": "BLUE",
    "gift_theme": ["BIRTHDAY_60TH"],
    "purpose_tags": ["다도", "선물"],
    "making_story": "물레로 형태를 잡은 뒤...",
    "usage_care": "차를 우린 뒤 미지근한 물로...",
    "status": "ON_SALE"
  }
}
```

| 필드 | 필수 | 설명 |
|------|------|------|
| artisan.artisan_id | ✅ | 백엔드 발급 장인 ID |
| artisan.business_name | ✅ | 공방/장인명 |
| artisan.certification_level | ⬜ | 등급 enum (랭킹 가중) |
| artisan.region | ⬜ | 지역 |
| product.product_id | ✅ | 백엔드 발급 상품 ID |
| product.name | ✅ | 작품명 |
| product.category_code | ⬜ | 대분류 (POTTERY 등) |
| product.subcategory_code | ⬜ | 세부 분류 (다완·수반 등). 임베딩에 포함 |
| product.material | ⬜ | 소재 |
| product.price | ⬜ | 가격 |
| product.color | ⬜ | 색상 enum (단일값) |
| product.gift_theme | ⬜ | 선물 테마 enum 배열. 부스팅용 |
| product.purpose_tags | ⬜ | 용도 태그 |
| **product.making_story** | ✅ | **제작 이야기 — 서사 핵심** |
| **product.usage_care** | ✅ | **관리법 — 서사 핵심** |
| product.status | ⬜ | 판매 상태. 검색은 ON_SALE만 노출 |

**Response**

```json
{ "success": true, "product_id": 1, "embedded": true }
```

> **artisan은 upsert 처리** (한 장인이 여러 상품을 올려도 장인 정보 중복 저장 방지)

---

### 4-4. PUT /ai/products/{product_id} — 상품 수정 (재임베딩)

변경된 필드만 전달. `making_story`·`usage_care`·`name`·`material`·`subcategory_code` 등 임베딩 대상 필드가 바뀌면 재임베딩을 트리거한다.

**Request**

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

> `re_embedded`는 임베딩 대상 필드가 변경되어 재임베딩이 수행됐는지 여부. 가격만 바뀐 경우 `false`.

---

### 4-5. DELETE /ai/products/{product_id} — 상품 삭제

**Response**

```json
{ "success": true, "product_id": 1, "deleted": true }
```

---

### 4-6. GET /ai/health — 상태 확인

**Response**

```json
{ "status": "ok", "db": "connected", "embedder": "loaded" }
```

---

## 5. 벡터 DB 스키마

### artisans (장인)

```sql
CREATE TABLE artisans (
  artisan_id           BIGINT PRIMARY KEY,
  business_name        TEXT NOT NULL,
  certification_level  TEXT,
  region               TEXT
);
```

### products (상품)

```sql
CREATE TABLE products (
  product_id        BIGINT PRIMARY KEY,
  artisan_id        BIGINT REFERENCES artisans(artisan_id),

  -- 백엔드가 전달하는 값
  name              TEXT NOT NULL,
  category_code     TEXT,
  subcategory_code  TEXT,
  material          TEXT,
  price             INTEGER,
  color             TEXT,
  gift_theme        TEXT[],
  purpose_tags      TEXT[],
  making_story      TEXT,
  usage_care        TEXT,
  status            TEXT,

  -- 파이프라인 자동 생성
  embedding_text    TEXT,
  embedding         VECTOR(1024),       -- BGE-M3
  search_text       TEXT,               -- BM25용 형태소 텍스트
  evidence          JSONB               -- 환각 방어 근거
);

CREATE INDEX ON products USING hnsw (embedding vector_cosine_ops);
```

### 파생 필드 생성 규칙

```
embedding_text = name + " " + category(한글) + " " + subcategory
                 + " " + material + " " + making_story + " " + usage_care

search_text    = embedding_text를 형태소 분석(Kiwi) → BM25 색인

evidence = {
  "artisan_input": making_story + usage_care 원문,
  "verified":      certification_level 값,
  "ai_inference":  null
}
```

> 장인 소개(introduction)는 현재 백엔드 데이터에 없어 embedding_text에서 제외한다.

### 검색·랭킹에서의 필드 역할

| 단계 | 사용 필드 |
|------|----------|
| 벡터 검색 | embedding (← embedding_text) |
| 키워드 검색 | search_text (BM25) |
| 하드 필터 | price, color, status(ON_SALE) |
| 부스팅 | gift_theme (있으면 가점, 없어도 제외 안 함) |
| 랭킹 가중 | certification_level (등급) |
| 추천이유 생성 | evidence (환각 방어) |

**등급 가중치** (유사도 점수에 가산):

| 등급 | 가산 |
|------|------|
| NATIONAL_INTANGIBLE_HERITAGE (국가무형유산) | +0.30 |
| MASTER_CRAFTSMAN (명장) | +0.20 |
| SENIOR_CRAFTSMAN (숙련장인) | +0.10 |
| YOUNG_CRAFTSMAN (청년장인) | +0.05 |

> **하드 필터 vs 벡터 기준:** 사용자가 정확히 말하는 것(price·color·gift_theme)은 필터/부스팅, 종목명을 모르고 말할 수 있는 것(category·subcategory·material)은 벡터 검색에 맡긴다.

---

## 6. Enum 값 목록

### certification_level (등급)

```
NATIONAL_INTANGIBLE_HERITAGE  국가무형유산
MASTER_CRAFTSMAN              명장
SENIOR_CRAFTSMAN              숙련장인
YOUNG_CRAFTSMAN               청년장인
```

### gift_theme (선물 테마)

```
HOUSEWARMING   집들이
WEDDING        웨딩
PARENTS        부모님
PROMOTION      승진
BIRTHDAY_60TH  돌·환갑
BOSS           상사
FRIEND         친구
CORPORATE      기업·단체
COUPLE         연인
```

### color (색상)

```
WHITE / BLACK / GRAY / RED / BLUE / GREEN / BROWN
```

### status (판매 상태)

```
ON_SALE   판매중 (검색 노출)
SOLD_OUT  품절
DRAFT     임시저장
HIDDEN    숨김
```

---

## 7. 백엔드에 요청하는 사항

1. **`/ai/chat` 호출 시 소비자 원문을 그대로 전달** — 임의 요약·가공 시 의도분류가 흔들린다
2. **상품 생성·수정·삭제 이벤트를 빠짐없이 전달** — 누락 시 검색DB와 실제 상품 목록이 어긋난다 (개발 단계는 CSV 벌크 적재, 실서비스는 `/ai/products` 계열)
3. **history는 최근 5~6턴 상한으로 전달** — 전체 이력은 프롬프트를 무겁게 한다
4. **enum 값 체계 합의** — certification_level·gift_theme·color·status 목록을 양측이 공유
5. **making_story·usage_care 원문 전달** — 추천 품질의 핵심 (실데이터에 포함 확인됨)

---

## 부록. 예상 대화 흐름 (멀티턴 예시)

```
소비자: "엄마 환갑 선물 5만원대"
  → AI: 추천 A, B, C + reply + suggestions

소비자: "좀 더 고급스러운 걸로"        ← history 필요 (앞 맥락 기준)
  → AI: 재추천 (고급 라인 부스팅)

소비자: "그 중에 파란색 있어?"          ← history 필요
  → AI: color=BLUE 필터 반영
```

> 뒤 두 질문은 앞 맥락 없이는 해석 불가 → history optional 설계의 근거.

---

*본 명세는 실데이터 구조(product/artisan/category/subcategory CSV)를 반영한 v2다.
주요 변경: 필드명(name·category_code·business_name), 추가(subcategory_code·status),
제거(introduction·production_period_days), certification_level 영문 4종 enum,
gift_theme 부스팅 전환, 임베딩 모델 BGE-M3, 응답에 suggestions 추가, extracted 제거.
2026-09-14 추가: history sender/role 형식 정정, reason 필드 제거, product_ids 정수 배열 통합.*
