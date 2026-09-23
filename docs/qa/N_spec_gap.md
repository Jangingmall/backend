# N. SPEC GAP List

| ID | Level | Location | Problem | Impact | Suggested Fix |
|----|-------|----------|---------|--------|---------------|
| SPEC-001 | WARN | GET /api/content/products/{productId}/generations/{generationId} | operationId "generation-poll-" 끝에 trailing hyphen — 불완전 ID | operationId 수정 필요 (BE) | openapi.json 생성 로직 확인 |
| SPEC-001 | WARN | GET /api/notifications/{notificationId} | operationId "notification-get-one-" 끝에 trailing hyphen — 불완전 ID | operationId 수정 필요 (BE) | openapi.json 생성 로직 확인 |
| SPEC-001 | WARN | DELETE /api/notifications/{notificationId} | operationId "notification-delete-" 끝에 trailing hyphen — 불완전 ID | operationId 수정 필요 (BE) | openapi.json 생성 로직 확인 |
| SPEC-001 | WARN | PATCH /api/notifications/{notificationId}/read | operationId "notification-read-" 끝에 trailing hyphen — 불완전 ID | operationId 수정 필요 (BE) | openapi.json 생성 로직 확인 |
| SPEC-002 | WARN | GET /api/notifications | operationId "notification-list-200" 에 HTTP 상태코드 접미사 포함 — 이름 오염 | operationId 정규화 필요 | REST Docs 테스트 operationId 수정 |
| SPEC-002 | WARN | POST /api/notifications | operationId "notification-create-200" 에 HTTP 상태코드 접미사 포함 — 이름 오염 | operationId 정규화 필요 | REST Docs 테스트 operationId 수정 |
| SPEC-002 | WARN | PATCH /api/notifications/read-all | operationId "notification-read-all-200" 에 HTTP 상태코드 접미사 포함 — 이름 오염 | operationId 정규화 필요 | REST Docs 테스트 operationId 수정 |
| SPEC-002 | WARN | GET /api/notifications/stream | operationId "notification-stream-200" 에 HTTP 상태코드 접미사 포함 — 이름 오염 | operationId 정규화 필요 | REST Docs 테스트 operationId 수정 |
| SPEC-002 | WARN | GET /api/notifications/unread-count | operationId "notification-unread-count-200" 에 HTTP 상태코드 접미사 포함 — 이름 오염 | operationId 정규화 필요 | REST Docs 테스트 operationId 수정 |
| SPEC-004 | WARN | GET /actuator/prometheus | 에러 응답 정의 없음 | Negative 테스트 기대값 불명확 | 최소 400/401/404 응답 추가 |
| SPEC-004 | WARN | PATCH /api/admin/artisans/applications/{applicationId}/pipeline | 에러 응답 정의 없음 | Negative 테스트 기대값 불명확 | 최소 400/401/404 응답 추가 |
| SPEC-004 | WARN | GET /api/admin/seller-applications | 에러 응답 정의 없음 | Negative 테스트 기대값 불명확 | 최소 400/401/404 응답 추가 |
| SPEC-004 | WARN | GET /api/admin/seller-applications/{applicationId} | 에러 응답 정의 없음 | Negative 테스트 기대값 불명확 | 최소 400/401/404 응답 추가 |
| SPEC-004 | WARN | POST /api/admin/seller-applications/{applicationId}/approve | 에러 응답 정의 없음 | Negative 테스트 기대값 불명확 | 최소 400/401/404 응답 추가 |
| SPEC-004 | WARN | POST /api/admin/seller-applications/{applicationId}/reject | 에러 응답 정의 없음 | Negative 테스트 기대값 불명확 | 최소 400/401/404 응답 추가 |
| SPEC-004 | WARN | POST /api/chatbot/sessions | 에러 응답 정의 없음 | Negative 테스트 기대값 불명확 | 최소 400/401/404 응답 추가 |
| SPEC-004 | WARN | DELETE /api/chatbot/sessions/{sessionId} | 에러 응답 정의 없음 | Negative 테스트 기대값 불명확 | 최소 400/401/404 응답 추가 |
| SPEC-004 | WARN | GET /api/chatbot/sessions/{sessionId}/messages | 에러 응답 정의 없음 | Negative 테스트 기대값 불명확 | 최소 400/401/404 응답 추가 |
| SPEC-004 | WARN | POST /api/chatbot/sessions/{sessionId}/messages | 에러 응답 정의 없음 | Negative 테스트 기대값 불명확 | 최소 400/401/404 응답 추가 |
| SPEC-004 | WARN | POST /api/content/products/{productId}/contents/{contentId}/submit | 에러 응답 정의 없음 | Negative 테스트 기대값 불명확 | 최소 400/401/404 응답 추가 |
| SPEC-004 | WARN | GET /api/member/artisans | 에러 응답 정의 없음 | Negative 테스트 기대값 불명확 | 최소 400/401/404 응답 추가 |
| SPEC-004 | WARN | POST /api/member/artisans/applications | 에러 응답 정의 없음 | Negative 테스트 기대값 불명확 | 최소 400/401/404 응답 추가 |
| SPEC-004 | WARN | GET /api/member/artisans/applications/me | 에러 응답 정의 없음 | Negative 테스트 기대값 불명확 | 최소 400/401/404 응답 추가 |
| SPEC-004 | WARN | GET /api/member/artisans/me | 에러 응답 정의 없음 | Negative 테스트 기대값 불명확 | 최소 400/401/404 응답 추가 |
| SPEC-004 | WARN | PATCH /api/member/artisans/me | 에러 응답 정의 없음 | Negative 테스트 기대값 불명확 | 최소 400/401/404 응답 추가 |
| SPEC-004 | WARN | GET /api/member/artisans/{artisanId} | 에러 응답 정의 없음 | Negative 테스트 기대값 불명확 | 최소 400/401/404 응답 추가 |
| SPEC-004 | WARN | POST /api/member/email/verification-code | 에러 응답 정의 없음 | Negative 테스트 기대값 불명확 | 최소 400/401/404 응답 추가 |
| SPEC-004 | WARN | POST /api/member/email/verify | 에러 응답 정의 없음 | Negative 테스트 기대값 불명확 | 최소 400/401/404 응답 추가 |
| SPEC-004 | WARN | POST /api/member/logout | 에러 응답 정의 없음 | Negative 테스트 기대값 불명확 | 최소 400/401/404 응답 추가 |
| SPEC-004 | WARN | DELETE /api/member/me | 에러 응답 정의 없음 | Negative 테스트 기대값 불명확 | 최소 400/401/404 응답 추가 |
| SPEC-004 | WARN | POST /api/member/me/addresses | 에러 응답 정의 없음 | Negative 테스트 기대값 불명확 | 최소 400/401/404 응답 추가 |
| SPEC-004 | WARN | DELETE /api/member/me/addresses/{addressId} | 에러 응답 정의 없음 | Negative 테스트 기대값 불명확 | 최소 400/401/404 응답 추가 |
| SPEC-004 | WARN | PATCH /api/member/me/addresses/{addressId} | 에러 응답 정의 없음 | Negative 테스트 기대값 불명확 | 최소 400/401/404 응답 추가 |
| SPEC-004 | WARN | PATCH /api/member/me/password | 에러 응답 정의 없음 | Negative 테스트 기대값 불명확 | 최소 400/401/404 응답 추가 |
| SPEC-004 | WARN | GET /api/member/me/reviews | 에러 응답 정의 없음 | Negative 테스트 기대값 불명확 | 최소 400/401/404 응답 추가 |
| SPEC-004 | WARN | GET /api/member/me/reviews/writable | 에러 응답 정의 없음 | Negative 테스트 기대값 불명확 | 최소 400/401/404 응답 추가 |
| SPEC-004 | WARN | GET /api/member/me/wishes/{productId} | 에러 응답 정의 없음 | Negative 테스트 기대값 불명확 | 최소 400/401/404 응답 추가 |
| SPEC-004 | WARN | POST /api/member/oauth2/complete-profile | 에러 응답 정의 없음 | Negative 테스트 기대값 불명확 | 최소 400/401/404 응답 추가 |
| SPEC-004 | WARN | POST /api/member/oauth2/exchange | 에러 응답 정의 없음 | Negative 테스트 기대값 불명확 | 최소 400/401/404 응답 추가 |
| SPEC-004 | WARN | GET /api/member/oauth2/kakao | 에러 응답 정의 없음 | Negative 테스트 기대값 불명확 | 최소 400/401/404 응답 추가 |
| SPEC-004 | WARN | POST /api/member/token/refresh | 에러 응답 정의 없음 | Negative 테스트 기대값 불명확 | 최소 400/401/404 응답 추가 |
| SPEC-004 | WARN | GET /api/notifications | 에러 응답 정의 없음 | Negative 테스트 기대값 불명확 | 최소 400/401/404 응답 추가 |
| SPEC-004 | WARN | POST /api/notifications | 에러 응답 정의 없음 | Negative 테스트 기대값 불명확 | 최소 400/401/404 응답 추가 |
| SPEC-004 | WARN | PATCH /api/notifications/read-all | 에러 응답 정의 없음 | Negative 테스트 기대값 불명확 | 최소 400/401/404 응답 추가 |
| SPEC-004 | WARN | GET /api/notifications/stream | 에러 응답 정의 없음 | Negative 테스트 기대값 불명확 | 최소 400/401/404 응답 추가 |
| SPEC-004 | WARN | GET /api/notifications/unread-count | 에러 응답 정의 없음 | Negative 테스트 기대값 불명확 | 최소 400/401/404 응답 추가 |
| SPEC-004 | WARN | POST /api/payments | 에러 응답 정의 없음 | Negative 테스트 기대값 불명확 | 최소 400/401/404 응답 추가 |
| SPEC-004 | WARN | GET /api/payments/cart | 에러 응답 정의 없음 | Negative 테스트 기대값 불명확 | 최소 400/401/404 응답 추가 |
| SPEC-004 | WARN | POST /api/payments/cart/items | 에러 응답 정의 없음 | Negative 테스트 기대값 불명확 | 최소 400/401/404 응답 추가 |
| SPEC-004 | WARN | DELETE /api/payments/cart/items | 에러 응답 정의 없음 | Negative 테스트 기대값 불명확 | 최소 400/401/404 응답 추가 |

총 84개 SPEC GAP 감지 (상위 50개 표시)

## 주요 비즈니스 SPEC_GAP (OpenAPI로 결정 불가)

| # | 항목 | 확인 대상 |
|---|------|---------|
| 1 | 주문 상태 enum 전체 목록 (CREATED/PAID/SHIPPED/...) | Backend |
| 2 | 반품 가능 조건 (배송완료 후 N일 이내) | Product Owner |
| 3 | 결제 실패 시 주문 자동 취소 여부 | Backend |
| 4 | 동일 상품 장바구니 중복 추가 정책 (수량 누적 vs 에러) | Backend |
| 5 | 회원 탈퇴 후 재가입 가능 여부 및 기간 | Product Owner |
| 6 | 토큰 만료 시간 (Access/Refresh 각각) | Backend |
| 7 | AI 생성 status enum (PROCESSING/COMPLETED/FAILED?) | Backend |
| 8 | 이미지 presigned URL 만료 시간 | Backend |
| 9 | 장인 신청 승인/거절 후 이메일 알림 여부 | Product Owner |
| 10 | 챗봇 세션 유효 기간 | Backend |
