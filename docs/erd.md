# 장인몰 백엔드 ERD

> 이 문서는 백엔드(Spring/PostgreSQL)가 소유하는 테이블 전체를 대상으로 한다.  
> AI파트의 pgvector DB(artisans·products w/ embedding)는 동기화 수신처(외부 경계)이며 이 ERD에 포함하지 않는다.

---

```mermaid
erDiagram

%% ──────────────────────────────────────────
%% 회원 도메인
%% ──────────────────────────────────────────

member {
    BIGINT      id              PK
    VARCHAR     email           UK
    VARCHAR     password_hash
    VARCHAR     name
    VARCHAR     phone
    VARCHAR     provider        "LOCAL | KAKAO | GOOGLE"
    VARCHAR     provider_id
    VARCHAR     role            "USER | ARTISAN | ADMIN"
    BOOLEAN     email_verified
    TIMESTAMP   created_at
    TIMESTAMP   updated_at
    TIMESTAMP   deleted_at
}

member_settings {
    BIGINT      id              PK
    BIGINT      member_id       FK
    BOOLEAN     notification_enabled
    BOOLEAN     marketing_agreed
    TIMESTAMP   updated_at
}

address {
    BIGINT      id              PK
    BIGINT      member_id       FK
    VARCHAR     recipient_name
    VARCHAR     phone
    VARCHAR     zip_code
    VARCHAR     address1
    VARCHAR     address2
    BOOLEAN     is_default
    TIMESTAMP   created_at
}

recent_view {
    BIGINT      id              PK
    BIGINT      member_id       FK "nullable — 게스트는 쿠키 식별"
    VARCHAR     guest_id
    BIGINT      product_id      FK
    TIMESTAMP   viewed_at
}

%% ──────────────────────────────────────────
%% 장인 도메인
%% ──────────────────────────────────────────

artisan {
    BIGINT      id              PK
    BIGINT      member_id       FK  UK "1:1"
    VARCHAR     business_name
    VARCHAR     craft           "종목"
    VARCHAR     region
    TEXT        introduction
    VARCHAR     certification_level "보유자 | 전승교육사 | 이수자 | 일반"
    TIMESTAMP   certified_at
    TIMESTAMP   created_at
    TIMESTAMP   updated_at
}

artisan_application {
    BIGINT      id              PK
    BIGINT      member_id       FK
    VARCHAR     craft
    TEXT        introduction
    VARCHAR     certification_level
    VARCHAR     status          "PENDING | APPROVED | REJECTED"
    TEXT        reject_reason
    TIMESTAMP   applied_at
    TIMESTAMP   reviewed_at
}

artisan_subscription {
    BIGINT      id              PK
    BIGINT      member_id       FK  "구독한 소비자"
    BIGINT      artisan_id      FK
    BOOLEAN     notification_on
    TIMESTAMP   subscribed_at
}

%% ──────────────────────────────────────────
%% 상품 도메인
%% ──────────────────────────────────────────

product {
    BIGINT      id              PK
    BIGINT      artisan_id      FK
    VARCHAR     name
    TEXT        description
    INTEGER     price
    INTEGER     stock
    VARCHAR     status          "ON_SALE | SOLD_OUT | HIDDEN | DRAFT"
    VARCHAR     category
    VARCHAR     material
    VARCHAR     color           "WHITE | BLACK | GRAY | RED | BLUE | GREEN | BROWN"
    VARCHAR     gift_theme      "HOUSEWARMING | WEDDING | PARENTS | PROMOTION | BIRTHDAY_60TH | BOSS | FRIEND | CORPORATE"
    TEXT_ARRAY  purpose_tags
    INTEGER     shipping_fee
    INTEGER     free_shipping_threshold
    INTEGER     production_period_days
    BOOLEAN     is_limited
    BOOLEAN     is_custom_order
    BOOLEAN     is_single_item
    BOOLEAN     has_gift_wrap
    BOOLEAN     engraving_available
    INTEGER     sales_count
    INTEGER     wish_count
    DECIMAL     rating_avg
    VARCHAR     model_name
    VARCHAR     size
    TEXT_ARRAY  components
    VARCHAR     manufacturer
    VARCHAR     country_of_origin
    VARCHAR     as_manager_contact
    TEXT        warranty_policy
    TIMESTAMP   created_at
    TIMESTAMP   updated_at
    TIMESTAMP   deleted_at
}

option_group {
    BIGINT      id              PK
    BIGINT      product_id      FK
    VARCHAR     type            "REQUIRED | OPTIONAL | TEXT"
    VARCHAR     name
    INTEGER     sort_order
}

option_choice {
    BIGINT      id              PK
    BIGINT      option_group_id FK
    VARCHAR     name
    INTEGER     price_delta
    INTEGER     stock
}

product_image {
    BIGINT      id              PK
    BIGINT      product_id      FK
    BIGINT      image_id        FK
    VARCHAR     alt
    INTEGER     sort_order
    BOOLEAN     is_thumbnail
}

wish {
    BIGINT      id              PK
    BIGINT      member_id       FK
    BIGINT      product_id      FK
    TIMESTAMP   created_at
}

question {
    BIGINT      id              PK
    BIGINT      product_id      FK
    BIGINT      member_id       FK
    TEXT        content
    BOOLEAN     is_secret
    TIMESTAMP   created_at
}

question_answer {
    BIGINT      id              PK
    BIGINT      question_id     FK  UK "1:1"
    BIGINT      artisan_id      FK
    TEXT        content
    TIMESTAMP   created_at
}

review {
    BIGINT      id              PK
    BIGINT      product_id      FK
    BIGINT      member_id       FK
    BIGINT      order_item_id   FK  UK "주문 확인 후 1회만"
    INTEGER     rating          "1~5"
    TEXT        content
    TIMESTAMP   created_at
    TIMESTAMP   updated_at
}

%% ──────────────────────────────────────────
%% 이미지 도메인
%% ──────────────────────────────────────────

image {
    BIGINT      id              PK
    BIGINT      uploader_id     FK  "member.id"
    VARCHAR     s3_key
    BOOLEAN     is_used
    TIMESTAMP   uploaded_at
}

image_variant {
    BIGINT      id              PK
    BIGINT      image_id        FK
    VARCHAR     url
    INTEGER     width
    INTEGER     height
    VARCHAR     format          "webp | jpg | png"
}

%% ──────────────────────────────────────────
%% 결제 도메인
%% ──────────────────────────────────────────

cart {
    BIGINT      id              PK
    BIGINT      member_id       FK  "nullable — 게스트는 guest_cart_id"
    VARCHAR     guest_cart_id
    TIMESTAMP   updated_at
}

cart_item {
    BIGINT      id              PK
    BIGINT      cart_id         FK
    BIGINT      product_id      FK
    INTEGER     quantity
    JSONB       selected_options
    TIMESTAMP   added_at
}

order {
    BIGINT      id              PK
    BIGINT      member_id       FK
    BIGINT      address_id      FK  "주문 시점 스냅샷"
    VARCHAR     order_number    UK
    VARCHAR     status          "CREATED | PAID | PAYMENT_FAILED | CANCELED | DELIVERED"
    INTEGER     total_amount
    TIMESTAMP   created_at
    TIMESTAMP   updated_at
}

order_item {
    BIGINT      id              PK
    BIGINT      order_id        FK
    BIGINT      product_id      FK
    VARCHAR     product_name    "스냅샷"
    INTEGER     price           "스냅샷"
    INTEGER     quantity
    JSONB       selected_options "스냅샷"
}

delivery {
    BIGINT      id              PK
    BIGINT      order_id        FK  UK
    VARCHAR     carrier
    VARCHAR     tracking_number
    VARCHAR     status
    TIMESTAMP   shipped_at
    TIMESTAMP   delivered_at
}

payment {
    BIGINT      id              PK
    BIGINT      order_id        FK  UK
    VARCHAR     payment_key     "토스페이먼츠 키"
    VARCHAR     method          "CARD | VIRTUAL_ACCOUNT | TRANSFER"
    INTEGER     amount
    VARCHAR     status          "READY | DONE | CANCELED | PARTIAL_CANCELED | FAILED"
    TIMESTAMP   approved_at
    TIMESTAMP   canceled_at
}

payment_method {
    BIGINT      id              PK
    BIGINT      member_id       FK
    VARCHAR     type            "CARD | VIRTUAL_ACCOUNT"
    VARCHAR     card_company
    VARCHAR     card_number_masked
    VARCHAR     billing_key
    BOOLEAN     is_default
    TIMESTAMP   created_at
}

refund_account {
    BIGINT      id              PK
    BIGINT      member_id       FK
    VARCHAR     bank
    VARCHAR     account_number
    VARCHAR     account_holder
    TIMESTAMP   created_at
}

%% ──────────────────────────────────────────
%% 알림 도메인
%% ──────────────────────────────────────────

notification {
    BIGINT      id              PK
    BIGINT      member_id       FK
    VARCHAR     type            "ORDER | DELIVERY | REVIEW | ARTISAN_NEW | SYSTEM"
    TEXT        message
    VARCHAR     link
    BOOLEAN     is_read
    TIMESTAMP   created_at
}

%% ──────────────────────────────────────────
%% 컨텐츠 도메인 (AI 상세페이지)
%% ──────────────────────────────────────────

interview {
    BIGINT      id              PK
    BIGINT      product_id      FK  UK
    TEXT        making_story    "어떻게 만드셨나요 — AI 임베딩 핵심 서사"
    TEXT        usage_care      "관리법 — AI 임베딩 핵심 서사"
    TIMESTAMP   created_at
    TIMESTAMP   updated_at
}

generation {
    BIGINT      id              PK
    BIGINT      product_id      FK
    VARCHAR     status          "PROCESSING | COMPLETED | FAILED"
    TIMESTAMP   requested_at
    TIMESTAMP   completed_at
}

content {
    BIGINT      id              PK
    BIGINT      product_id      FK
    BIGINT      generation_id   FK
    INTEGER     version
    VARCHAR     status          "DRAFT | APPROVED | REJECTED | PUBLISHED"
    TIMESTAMP   created_at
}

content_block {
    BIGINT      id              PK
    BIGINT      content_id      FK
    INTEGER     sort_order
    VARCHAR     tag             "h2 | p | img | video"
    TEXT        text
    BIGINT      image_id        FK  "nullable"
    VARCHAR     video_url
}

%% ──────────────────────────────────────────
%% 챗봇 도메인
%% ──────────────────────────────────────────

chatbot_session {
    BIGINT      id              PK
    VARCHAR     session_key     UK  "외부 노출 ID"
    BIGINT      member_id       FK  "nullable — 게스트 허용"
    TIMESTAMP   created_at
    TIMESTAMP   expired_at
}

chatbot_message {
    BIGINT      id              PK
    BIGINT      session_id      FK
    VARCHAR     role            "user | assistant"
    TEXT        content         "대화 텍스트"
    VARCHAR     intent          "AI 의도분류 결과 (assistant 메시지에만)"
    JSONB       extracted       "AI 추출 조건 {maxPrice, purpose, vibe}"
    TIMESTAMP   created_at
}

chatbot_recommendation {
    BIGINT      id              PK
    BIGINT      message_id      FK  "assistant 메시지 ID"
    BIGINT      product_id      FK
    TEXT        reason          "AI 생성 추천이유 (evidence 기반)"
    INTEGER     sort_order
}

%% ──────────────────────────────────────────
%% 관계
%% ──────────────────────────────────────────

member            ||--o{ address                 : "배송지"
member            ||--o| member_settings         : "환경설정"
member            ||--o| artisan                 : "장인 프로필"
member            ||--o{ artisan_application     : "가입신청"
member            ||--o{ artisan_subscription    : "장인구독"
member            ||--o{ wish                    : "찜"
member            ||--o{ recent_view             : "최근본상품"
member            ||--o{ review                  : "후기"
member            ||--o{ notification            : "알림"
member            ||--o{ payment_method          : "결제수단"
member            ||--o{ refund_account          : "환불계좌"
member            ||--o{ order                   : "주문"
member            ||--o| cart                    : "장바구니"
member            ||--o{ image                   : "업로드"
member            ||--o{ chatbot_session         : "챗봇세션"

artisan           ||--o{ product                 : "상품"
artisan           ||--o{ question_answer         : "문의답변"
artisan_application }o--|| member               : "신청자"
artisan_subscription }o--|| artisan             : "구독대상"

product           ||--o{ option_group            : "옵션그룹"
option_group      ||--o{ option_choice           : "옵션선택지"
product           ||--o{ product_image           : "상품이미지"
product           ||--o{ wish                    : "찜"
product           ||--o{ question                : "문의"
product           ||--o{ review                  : "후기"
product           ||--o{ recent_view             : "최근조회"
product           ||--o| interview               : "취재데이터"
product           ||--o{ generation              : "AI생성요청"
product           ||--o{ content                 : "AI콘텐츠"

image             ||--o{ image_variant           : "variant"
image             ||--o{ product_image           : "상품이미지"
image             ||--o{ content_block           : "콘텐츠블록"

question          ||--o| question_answer         : "답변"

cart              ||--o{ cart_item               : "장바구니항목"
cart_item         }o--|| product                 : "상품"

order             ||--o{ order_item              : "주문항목"
order             ||--o| delivery                : "배송"
order             ||--o| payment                 : "결제"
order_item        }o--|| product                 : "상품스냅샷"
order_item        ||--o| review                  : "후기대상"

generation        ||--o{ content                 : "생성결과"
content           ||--o{ content_block           : "블록"

chatbot_session   ||--o{ chatbot_message         : "메시지"
chatbot_message   ||--o{ chatbot_recommendation  : "추천상품"
chatbot_recommendation }o--|| product            : "추천된상품"
```

---

## 주요 설계 결정

| 항목 | 결정 | 근거 |
| --- | --- | --- |
| `artisan` ↔ `member` | 1:1 별도 테이블 | ADMIN 승인 후 role 전환 — 승인 전후 상태 분리 필요 |
| `cart.member_id` nullable | 게스트 `guest_cart_id` 병행 | 비회원 장바구니 + 로그인 시 병합 (`POST /api/payments/cart/merge`) |
| `chatbot_session.member_id` nullable | 게스트 챗봇 허용 | PHASE2-2 §5-13: Guest 챗봇 접근 ✅ |
| `order_item` 스냅샷 | `product_name`, `price`, `selected_options` 복사 저장 | 주문 후 상품 수정·삭제되어도 이력 보존 |
| `content_block` | AI가 HTML 생성 → BE가 분해해 블록 JSON으로 저장 | AI 출력(HTML)을 BE가 파싱해 `{tag, text, imageUrl}` 블록으로 쪼개 저장. FE는 블록 단위 수신·편집 |
| `interview.making_story` / `usage_care` | BE DB 보관 + AI 동기화 | AI 임베딩 핵심 서사. BE가 원본 소유, AI에 `/ai/products`로 전달 |
| `chatbot_recommendation` | `chatbot_message` 자식 테이블 | `product_id + reason` 이력 보존. AI 응답 구조 그대로 (`ai_chat_bot.md §4-1`) |
| `evidence` 필드 | AI DB에만 존재 | 환각 방어 근거는 AI pgvector DB 책임. BE는 원문(`making_story`, `usage_care`)만 보관 |

---

## AI 동기화 경계

BE가 소유한 데이터 중 AI pgvector DB로 동기화되는 흐름:

| 트리거 | BE API | AI 엔드포인트 | 전달 데이터 |
| --- | --- | --- | --- |
| 상품 게시 | `POST /api/content/products/{productId}/publish` | `POST /ai/products` | artisan + product 풀데이터 (making_story·usage_care·gift_theme·purpose_tags·color 포함) |
| 상품 수정 | `PATCH /api/products/{productId}` | `PUT /ai/products/{id}` | 변경 필드만 |
| 상품 삭제 | `DELETE /api/products/{productId}` | `DELETE /ai/products/{id}` | product_id |
