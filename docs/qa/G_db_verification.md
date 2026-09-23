# G. DB Verification Requirements

DB Schema가 제공되지 않아 SQL을 생성하지 않음 (DB_SCHEMA_REQUIRED).
아래는 API 응답만으로 검증 불가한 항목 목록.

## DB_SCHEMA_REQUIRED 항목

| API | 검증 필요 항목 | 이유 |
|-----|---------------|------|
| POST /api/payments/confirm | 결제 레코드 생성 여부, amount 정합성 | 이중 청구 방지 |
| POST /api/payments/orders | 재고 차감 여부 | 동시 주문 시 oversell 방지 |
| POST /api/payments/returns | 반품 상태 전이 | API 응답에 상태 미포함 가능 |
| DELETE /api/member/me | soft delete 여부 | 재가입 정책 확인 |
| POST /api/member/signup | 이메일 중복 방지 unique 제약 | |
| POST /api/payments/confirm (webhook) | Toss webhook 멱등성 — 동일 paymentKey 중복 저장 방지 | |
| POST /api/payments/cart/items | 동일 상품 수량 누적 vs 신규 행 생성 정책 | SPEC_GAP |
| PATCH /api/products/{productId}/status | status 컬럼 변경 여부 | |

## 요청 사항 (Backend 팀)

1. 결제 테이블 schema (payments, purchase_orders)
2. 주문 상태 enum 전체 목록
3. 반품 상태 전이 다이어그램
4. 회원 탈퇴 soft delete 여부
5. 재고 차감 시점 (주문 생성 시 vs 결제 확인 시)
