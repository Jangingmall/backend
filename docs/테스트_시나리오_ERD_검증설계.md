# 테스트 시나리오 ↔ ERD 검증 설계

> 목적: 테스트 시나리오가 실제 ERD(물리설계)에 정확히 대응되는지 검증하고,
> ERD 제약 중 시나리오가 누락된 항목을 식별한다.
> 
> 기준 파일: `장인몰_ERD_물리설계.csv`, `테스트 시나리오.md`, `장인몰_동시성_처리_전략.csv`

---

## 1. 검증 방향

```
Forward (시나리오 → ERD)
  시나리오가 언급하는 컬럼·상태값·제약이 ERD에 실제로 존재하는가?
  → 없으면: 시나리오가 존재하지 않는 구조를 테스트하고 있음 (잘못된 시나리오)

Backward (ERD → 시나리오)
  ERD의 CHECK/UNIQUE/NOT NULL/낙관적 락이 대응하는 시나리오로 커버되고 있는가?
  → 없으면: 제약은 있는데 테스트가 빠진 커버리지 갭
```

---

## 2. 시나리오 분류

ERD-verifiable 시나리오와 behavioral 시나리오를 구분한다.
ERD 검증은 전자에만 적용한다.

| 분류 | 기준 | 예시 |
|---|---|---|
| **ERD-verifiable** | 특정 테이블·컬럼·상태값·UNIQUE·CHECK를 직접 참조 | "RETURN_REQUESTED 상태로 변경", "deleted_at 소프트 딜리트" |
| **Behavioral** | DB 구조가 아닌 인프라·프로토콜·타임아웃·외부 시스템 | SSE heartbeat, nginx buffering, PG 웹훅 서명, JWT 위변조 |

---

## 3. Forward 검증 — 현재 불일치 목록

직접 ERD를 대조해 확인된 결함이다. 개발자가 시나리오를 수정 또는 ERD를 업데이트해야 한다.

### 3-1. `deleted_at` 컬럼 — 존재하지 않음

| 항목 | 내용 |
|---|---|
| 시나리오 | "회원 탈퇴 시 소프트 딜리트(deleted_at) 처리" (회원 섹션) |
| ERD 실제 | `member.status` CHECK IN ('PENDING_VERIFICATION','ACTIVE','**WITHDRAWN**') |
| `deleted_at` 컬럼 | **없음** |
| 판정 | 시나리오가 존재하지 않는 컬럼을 언급 |
| 조치 | 시나리오를 "회원 탈퇴 시 status가 WITHDRAWN으로 변경"으로 수정 요청 (개발자 판단) |

### 3-2. `RETURN_REQUESTED` 주문 상태 — ✅ ERD 반영 완료

| 항목 | 내용 |
|---|---|
| 시나리오 | "반품 신청 시 주문 상태가 RETURN_REQUESTED로 변경" (Flow 3) |
| 조치 | `orders.status` CHECK에 `RETURN_REQUESTED` 추가, `order_return` 테이블 신규 추가 |
| CONC | CONC-017 중복 신청 방지 전략 추가 |

### 3-3. 챗봇 테이블 명칭 불일치

| 항목 | 내용 |
|---|---|
| 시나리오 | `POST /api/chatbot/sessions` (Flow 2) |
| ERD 실제 | `chat_session`, `chat_message`, `chat_recommendation` |
| 시나리오 명칭 | `chatbot_session` (일부), `chatbot` 접두사 혼용 |
| 판정 | ERD 테이블명과 API 경로·시나리오 명칭이 불일치 (기능 동일, 명칭만 다름) |
| 조치 | API 경로(`/chatbot`)와 ERD 테이블명(`chat_`) 중 하나로 통일 결정 필요 |

### 3-4. `product_review` 1회 작성 — ✅ ERD 반영 완료

| 항목 | 내용 |
|---|---|
| 시나리오 | "해당 order_item에 대해 1회만 작성 가능" (상품 섹션) |
| 조치 | `product_review UNIQUE(order_item_id)` 제약 추가 |

---

## 4. Backward 검증 — ERD 제약 중 시나리오 미커버 항목

ERD에 제약은 있으나 테스트 시나리오가 없는 갭이다.
AI 추가 규칙에 따라 이탤릭체 시나리오로 추가 가능하다.

| ERD 제약 | 테이블.컬럼 | 현재 시나리오 | 갭 |
|---|---|---|---|
| `product.price CHECK(price >= 0)` | product.price | 없음 | price=0 또는 음수 입력 시 INVALID_INPUT 검증 미존재 |
| `cart_item.quantity CHECK(quantity > 0)` | cart_item.quantity | 없음 | quantity=0 장바구니 담기 차단 검증 미존재 |
| `product_review.rating CHECK(1~5)` | product_review.rating | 없음 | rating 범위 외 입력 시 INVALID_INPUT 검증 미존재 |
| `member_social_account UNIQUE(registration_id, provider_user_id)` | member_social_account | 없음 | 동일 소셜 계정 중복 가입 차단 시나리오 미존재 |
| `CONC-016 Partial UNIQUE INDEX` (PENDING 장인 중복 신청) | seller_application | 없음 | 기존 PENDING 신청 중 재신청 시 CONFLICT 검증 미존재 |
| `seller_application.version` 낙관적 락 (CONC-005) | seller_application | 어드민 섹션에 일부 언급 | CONCURRENT_UPDATE 반환 시나리오 미존재 |
| `image_upload.expires_at` 배치 정리 | image_upload | "주기적으로 정리" | 실패 경로(정리 실패 시 관측) 시나리오 미존재 |
| `cart.guest_cart_id UNIQUE` | cart | 병합 시나리오 일부 | 동일 guest_cart_id 중복 생성 차단 미존재 |

---

## 5. 시나리오 추가 — ERD 갭 커버

위 4장의 갭 중 ERD-verifiable인 항목을 이탤릭 규칙으로 추가 대상 목록으로 정리한다.  
개발자 확인 후 `테스트 시나리오.md`에 반영한다.

### 추가 대상 (승인 필요)

**상품 섹션에 추가:**
- _상품 등록 시 price가 음수이면 INVALID_INPUT이 반환된다._
- _후기(review) 작성 시 rating이 1~5 범위를 벗어나면 INVALID_INPUT이 반환된다._

**결제 섹션에 추가:**
- _장바구니 담기 시 quantity가 0 이하이면 INVALID_INPUT이 반환된다._

**회원 섹션에 추가:**
- _동일 소셜 계정(provider + provider_user_id 조합)으로 중복 가입 시 CONFLICT가 반환된다._

**관리자 섹션에 추가:**
- _동일 장인 가입신청을 두 관리자가 동시에 처리하면 한쪽은 CONCURRENT_UPDATE(409)가 반환된다(낙관적 락 CONC-005)._
- _이미 PENDING 상태인 장인 가입신청이 있는 회원이 재신청 시 CONFLICT가 반환된다(CONC-016)._

---

## 6. 검증 실행 방법 (수동)

```
1. 시나리오에서 상태값 키워드를 추출한다.
   (RETURN_REQUESTED, DRAFT, ON_SALE, PAID, PENDING_REVIEW 등)

2. ERD_물리설계.csv에서 해당 테이블의 CHECK 컬럼을 grep한다.
   예) grep "orders" 장인몰_ERD_물리설계.csv | grep "CHECK"

3. 시나리오 언급 값이 CHECK IN (...) 목록에 있는지 확인한다.
   없으면 → Forward 불일치 (3장에 기록)

4. ERD의 UNIQUE/CHECK/낙관적 락 행을 순회하며
   각 제약에 대응하는 시나리오가 있는지 테스트 시나리오.md에서 검색한다.
   없으면 → Backward 갭 (4장에 기록, 5장에 추가 후보 작성)
```

---

## 7. 관련 문서

| 문서 | 경로 |
|---|---|
| ERD 물리설계 | `장인몰_ERD_물리설계.csv` |
| 동시성 전략 | `장인몰_동시성_처리_전략.csv` |
| 테스트 시나리오 | `테스트 시나리오.md` |
| 예외 설계 | `예외_설계.md` |
| 코드 규칙 | `코드_규칙.md` |
