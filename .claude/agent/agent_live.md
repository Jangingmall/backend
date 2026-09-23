r# Agent Live — 작업 충돌 방지 레지스트리

AI 에이전트가 작업 중인 범위를 등록한다.
새 작업 시작 전 이 파일을 읽고, 겹치는 파일/브랜치가 없는지 확인한다.

## 등록 형식

```
### [에이전트 식별자 또는 작업명]
- 브랜치: feat/xxx
- 상태: 진행 중 | 완료 | 중단
- 접근 파일 (패턴):
  - src/main/java/.../SomeService.java
  - src/main/java/.../SomeController.java
- 접근 금지 파일 (다른 에이전트 작업 중):
  - (없으면 생략)
- 작업 요약: 한 줄
- 시작일: YYYY-MM-DD
```

---

## 현재 활성 작업

### Claude-현재세션 (api-docs-consistency)
- 브랜치: `feat/api-docs-consistency`
- 상태: 완료
- 접근 파일:
  - `.claude/rules/openapi-conventions.md` (신규)
  - `gradle/documentation.gradle`
  - `src/main/java/com/jangingmall/backend/notification/presentation/NotificationController.java`
  - `src/main/java/com/jangingmall/backend/image/presentation/ImageController.java`
  - `src/test/java/com/jangingmall/backend/payment/presentation/PaymentControllerTest.java`
  - `src/test/java/com/jangingmall/backend/member/presentation/SellerApplicationControllerTest.java`
- 작업 요약: OpenAPI/ReDoc 일관성 17개 항목 규칙화 및 수정
- 시작일: 2026-09-18

### Claude-현재세션 (cicd-eks-gitops)
- 브랜치: `feat/enum-table-docs`
- 상태: 완료
- 접근 파일:
  - `.github/workflows/ci.yml`
  - `.github/workflows/cd.yml`
- 작업 요약: CI에 fix/** 추가 + Docker Build 검증 job 추가, CD를 develop 트리거 + OIDC + digest 산출 + EC2/SSM 제거로 전환
- 시작일: 2026-09-18



### Claude-현재세션 (order-history)
- 브랜치: `feat/order-history`
- 상태: 완료
- 접근 파일:
  - `src/main/java/com/jangingmall/backend/payment/domain/OrderStatus.java`
  - `src/main/java/com/jangingmall/backend/payment/domain/PurchaseOrder.java`
  - `src/main/java/com/jangingmall/backend/payment/application/DeliveryService.java`
  - `src/main/java/com/jangingmall/backend/member/presentation/MemberQueryController.java`
  - `src/main/java/com/jangingmall/backend/member/application/MemberQueryService.java`
  - `src/main/java/com/jangingmall/backend/member/application/MemberReadRepository.java`
  - `src/main/java/com/jangingmall/backend/member/infrastructure/MemberReadRepositoryImpl.java`
  - `src/test/java/com/jangingmall/backend/member/presentation/MemberQueryControllerTest.java`
  - `docs/주문이력_API_계약서.md`
- 작업 요약: 주문 이력 API 개선 (2.1~2.5) + IN_DELIVERY 상태 추가
- 시작일: 2026-09-18

---

### Claude-현재세션 (ai-generation-approval-flow)
- 브랜치: `feat/ai-generation-approval-flow`
- 상태: 완료
- 접근 파일:
  - `src/main/resources/application.yml`
  - `src/main/java/com/jangingmall/backend/content/domain/GenerationStatus.java`
  - `src/main/java/com/jangingmall/backend/content/domain/AiContentClient.java`
  - `src/main/java/com/jangingmall/backend/content/domain/ContentGeneration.java`
  - `src/main/java/com/jangingmall/backend/content/infrastructure/RestAiContentClient.java`
  - `src/main/java/com/jangingmall/backend/content/application/GenerationService.java`
  - `src/main/java/com/jangingmall/backend/content/presentation/ContentController.java`
  - `src/main/java/com/jangingmall/backend/content/application/ContentService.java`
  - `src/test/java/com/jangingmall/backend/content/presentation/ContentControllerTest.java`
  - `src/test/java/com/jangingmall/backend/content/presentation/AiCallbackControllerTest.java`
- 작업 요약: BE-E2E-1~4 — AI 승인 흐름 연결, DRAFT_READY 상태 추가, multipart 한도 상향
- 시작일: 2026-09-18

---

### Claude-현재세션 (user-journey-e2e)
- 브랜치: `feat/user-journey-e2e`
- 상태: 완료
- 접근 파일:
  - `src/test/java/com/jangingmall/backend/e2e/UserJourneyE2ETest.java` (신규)
  - `src/test/java/com/jangingmall/backend/e2e/StubPaymentGateway.java` (신규)
  - `src/main/java/com/jangingmall/backend/payment/infrastructure/JdbcCheckoutCatalog.java`
  - `src/main/java/com/jangingmall/backend/member/infrastructure/MemberOrderView.java`
  - `src/main/java/com/jangingmall/backend/member/infrastructure/MemberOrderItemView.java`
  - `src/main/java/com/jangingmall/backend/global/config/PermitAllPaths.java`
  - `src/main/resources/application.yml`
- 작업 요약: 가입→검색→장바구니→결제→주문완료→배송추적→환불 E2E 통합 테스트 (15개 테스트 전부 통과)
- 시작일: 2026-09-18

---

### Claude-현재세션 (sample-seed-data)
- 브랜치: `develop`
- 상태: 완료
- 접근 파일:
  - `src/main/resources/data.sql`
  - `src/main/resources/data-sequence-reset.sql` (신규)
  - `src/main/resources/application.yml`
- 작업 요약: CSV 샘플 데이터(장인 61명, 제품 729개) → data.sql seed 변환
- 시작일: 2026-09-21

---

---

### Claude-현재세션 (revalidate-webhook)
- 브랜치: `feat/revalidate-webhook`
- 상태: 완료
- 접근 파일:
  - `src/main/java/com/jangingmall/backend/revalidate/**` (신규 패키지)
  - `src/main/java/com/jangingmall/backend/product/application/ProductService.java`
  - `src/main/java/com/jangingmall/backend/content/application/ContentService.java`
  - `src/main/java/com/jangingmall/backend/member/application/ArtisanService.java`
  - `src/main/resources/application.yml`
  - `src/test/java/com/jangingmall/backend/revalidate/**` (신규)
- 작업 요약: Vercel 스테이징 캐시 재검증 웹훅 — HMAC-SHA256 서명, @TransactionalEventListener 발행
- 시작일: 2026-09-23

### Claude-현재세션 (be-qa-sheet)
- 브랜치: `docs/be-qa-sheet`
- 상태: 완료
- 접근 파일:
  - `docs/BE_QA_시트.csv` (신규)
- 작업 요약: Flow 1~4 + Risk Matrix 기반 BE QA 시트 25개 항목 작성
- 시작일: 2026-09-23

---

### Claude-현재세션 (payment-compensation)
- 브랜치: `feat/payment-compensation`
- 상태: 완료
- 접근 파일:
  - `src/main/java/com/jangingmall/backend/payment/application/PaymentService.java`
  - `src/test/java/com/jangingmall/backend/payment/application/PaymentServiceTest.java`
- 작업 요약: QA No.2 — PG 승인 후 로컬 저장 실패 시 paymentGateway.cancel() 보상 취소
- 시작일: 2026-09-23

---

### Claude-현재세션 (return-7day-deadline)
- 브랜치: `feat/return-7day-deadline`
- 상태: 완료
- 접근 파일:
  - `src/main/java/com/jangingmall/backend/payment/domain/PurchaseOrder.java`
  - `src/main/java/com/jangingmall/backend/payment/application/ReturnService.java`
  - `src/main/java/com/jangingmall/backend/payment/domain/OrderReturnRepository.java`
  - `src/main/java/com/jangingmall/backend/payment/application/OrderNotificationPublisher.java`
  - `src/main/java/com/jangingmall/backend/payment/infrastructure/JdbcOrderNotificationPublisher.java`
  - `src/main/java/com/jangingmall/backend/payment/application/ReturnStaleAlertScheduler.java` (신규)
  - `src/main/resources/db/migration/V5__add_delivered_at_to_orders.sql` (신규)
  - `src/test/java/com/jangingmall/backend/payment/application/ReturnServiceTest.java`
  - `src/test/java/com/jangingmall/backend/payment/application/ReturnStaleAlertSchedulerTest.java` (신규)
- 작업 요약: QA No.16(배송완료 7일 이내 반품조건) + No.18(24h 미처리 반품 관리자 알림 스케줄러)
- 시작일: 2026-09-23

---

### Claude-현재세션 (email-verification-and-account-lock)
- 브랜치: `feat/email-verification-and-account-lock`
- 상태: 완료
- 접근 파일:
  - `build.gradle`
  - `src/main/java/com/jangingmall/backend/member/application/EmailSender.java` (신규)
  - `src/main/java/com/jangingmall/backend/member/application/EmailVerificationStore.java` (신규)
  - `src/main/java/com/jangingmall/backend/member/application/EmailVerificationService.java` (신규)
  - `src/main/java/com/jangingmall/backend/member/application/LoginAttemptService.java` (신규)
  - `src/main/java/com/jangingmall/backend/member/infrastructure/EmailSenderConfiguration.java` (신규)
  - `src/main/java/com/jangingmall/backend/member/infrastructure/RedisEmailVerificationStore.java` (신규)
  - `src/main/java/com/jangingmall/backend/member/infrastructure/RedisLoginAttemptService.java` (신규)
  - `src/main/java/com/jangingmall/backend/member/presentation/MemberController.java`
  - `src/main/java/com/jangingmall/backend/member/presentation/dto/EmailVerificationRequest.java` (신규)
  - `src/main/java/com/jangingmall/backend/member/application/MemberAuthenticationService.java`
  - `src/main/java/com/jangingmall/backend/global/config/PermitAllPaths.java`
  - `src/test/java/com/jangingmall/backend/member/application/EmailVerificationServiceTest.java` (신규)
  - `src/test/java/com/jangingmall/backend/member/application/MemberAuthenticationServiceTest.java`
  - `src/test/java/com/jangingmall/backend/member/presentation/MemberControllerTest.java`
- 작업 요약: QA No.20(이메일 인증코드 6자리 발송/검증 API) + No.21(이메일 단위 5회 실패 시 30분 계정 잠금)
- 시작일: 2026-09-23

---

### Claude-현재세션 (public-api-docs)
- 브랜치: `feat/public-api-docs`
- 상태: 완료
- 접근 파일:
  - `gradle/documentation.gradle`
  - `.github/workflows/docs.yml` (신규)
  - `.gitignore`
  - `.claude/docs/openapiDocs/redocly/README.md`
- 작업 요약: 이력서용 공개 API 문서 — processOpenapiPublic + buildRedoclyPublic + GitHub Pages CI
- 시작일: 2026-09-23

---

### Claude-현재세션 (public-docs-artifacts)
- 브랜치: `feat/public-docs-artifacts`
- 상태: 진행 중
- 접근 파일:
  - `gradle/documentation.gradle`
  - `.github/workflows/docs.yml`
  - `.claude/docs/openapiDocs/redocly/README.md`
- 작업 요약: GitHub Pages 3개 산출물 추가 — Postman Collection(QA), TypeScript 타입(FE), HTML 요약(PM)
- 시작일: 2026-09-23

---

## 완료된 작업 (참고용)

(없음)
