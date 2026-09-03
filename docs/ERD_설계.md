# 장인몰 백엔드 ERD

> 이 문서는 백엔드(Spring/PostgreSQL)가 소유하는 테이블 전체를 대상으로 한다.  
> AI파트의 pgvector DB(artisans·products w/ embedding)는 동기화 수신처(외부 경계)이며 이 ERD에 포함하지 않는다.

---

<img width="5198" height="2772" alt="diagram_erd" src="https://github.com/user-attachments/assets/fbc61907-0444-460d-ac03-b2ed60a6ed2d" />

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
