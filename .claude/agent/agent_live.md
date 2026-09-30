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

### Claude-현재세션 (phase4-performance-after)
- 브랜치: `feat/phase4-performance`
- 상태: 진행 중
- 접근 파일:
  - `src/test/java/com/jangingmall/backend/e2e/UserJourneyE2ETest.java`
  - `src/main/java/com/jangingmall/backend/product/application/ProductService.java`
  - `src/main/java/com/jangingmall/backend/product/domain/Product.java`
  - `src/main/java/com/jangingmall/backend/image/application/ImageService.java`
  - `src/main/java/com/jangingmall/backend/image/domain/ImageUploadRepository.java`
  - `src/main/java/com/jangingmall/backend/content/application/ContentService.java`
  - `src/main/resources/db/migration/V6__performance_indexes.sql` (신규)
  - `src/main/resources/application.yml`
  - `build.gradle`
  - `src/gatling/java/**` (신규)
  - `docker/monitoring/**` (신규)
  - `docs/load-test/**` (신규)
- 작업 요약: Phase 4 — 통합 테스트 추가, N+1 배치 최적화, DB 인덱스, Redis 캐시, Prometheus+Grafana 모니터링, JaCoCo 95% 커버리지 설정
- 시작일: 2026-09-27

### Claude-현재세션 (csv-seed-migration)
- 브랜치: `feat/csv-seed-migration`
- 상태: 완료
- 접근 파일:
  - `src/main/resources/db/migration/V9__csv_seed_data.sql` (신규)
  - `docs/장인몰_샘플_artisan.csv`
  - `docs/장인몰_샘플_category.csv`
  - `docs/장인몰_샘플_product.csv`
- 작업 요약: CSV 원본 기반 Flyway V9 seed migration (장인 81명, 상품 729개)
- 시작일: 2026-09-28

### Claude-현재세션 (oauth2-kakao-naver)
- 브랜치: `feat/oauth2-kakao-naver`
- 상태: 진행 중
- 접근 파일:
  - `src/main/java/com/jangingmall/backend/global/config/PermitAllPaths.java`
  - `src/main/java/com/jangingmall/backend/global/config/SecurityConfig.java`
  - `src/main/java/com/jangingmall/backend/member/**` (OAuth2 관련)
  - `src/main/resources/application.yml`
- 작업 요약: 카카오·네이버 OAuth2 소셜 로그인 연동
- 시작일: 2026-09-28

### Claude-현재세션 (fix-dummy-image-urls)
- 브랜치: `feat/fix-dummy-image-urls`
- 상태: 완료
- 접근 파일:
  - `src/main/resources/data.sql`
- 작업 요약: dead domain 9개(youngnamyo/shindawan/kwangjuyo/dadowon/kpicaa/yugi/유기/onggi/mosi/namwonmokgi) 255개 thumbnail_url → 검증된 공개 URL로 교체

### Claude-현재세션 (approve-async-render)
- 브랜치: `develop`
- 상태: 완료
- 접근 파일:
  - `src/main/java/com/jangingmall/backend/content/application/AiRenderApprovalRequestedEvent.java` (신규)
  - `src/main/java/com/jangingmall/backend/content/application/AiRenderApprovalAsyncExecutor.java` (신규)
  - `src/main/java/com/jangingmall/backend/content/application/ContentService.java`
  - `src/test/java/com/jangingmall/backend/content/application/ContentServiceTest.java`
- 작업 요약: approve() @Transactional 안 동기 approveRender 호출 → AFTER_COMMIT @Async 이벤트 발행으로 분리 (DB 커넥션 풀 고갈 방지)
- 시작일: 2026-09-29

### Claude-현재세션 (ai-discord-notification)
- 브랜치: `develop`
- 상태: 완료
- 접근 파일:
  - `src/main/java/com/jangingmall/backend/content/infrastructure/DiscordNotificationService.java` (신규)
  - `src/main/java/com/jangingmall/backend/content/application/GenerationAsyncExecutor.java`
  - `src/test/java/com/jangingmall/backend/content/application/GenerationAsyncExecutorTest.java`
  - `src/main/resources/application.yml`
  - `http/generation.http` (신규)
- 작업 요약: AI 생성 요청/성공/실패 시 Discord 웹훅 알림 + console 로그, generation.http 테스트 파일
- 시작일: 2026-09-29

### Claude-현재세션 (ai-job-status-polling-31min)
- 브랜치: `claude/project-thread-tjtx2i`
- 상태: 완료
- 접근 파일:
  - `src/main/java/com/jangingmall/backend/content/application/GenerationDeadlineScheduler.java`
  - `src/test/java/com/jangingmall/backend/content/application/GenerationDeadlineSchedulerTest.java`
- 작업 요약: QUEUED generation을 31분 동안 1분마다 AI 상태 조회, 데드라인 시 최종 조회 후 FAILED (폴링·만료 스케줄 단일화)
- 시작일: 2026-09-30

### Claude-현재세션 (login-lock-429)
- 브랜치: `claude/zen-rubin-go05xr`
- 상태: 완료
- 접근 파일:
  - `src/main/java/com/jangingmall/backend/global/exception/DomainException.java`
  - `src/main/java/com/jangingmall/backend/global/exception/GlobalExceptionHandler.java`
  - `src/main/java/com/jangingmall/backend/member/application/MemberAuthenticationService.java`
  - `src/main/java/com/jangingmall/backend/member/infrastructure/RedisLoginAttemptService.java`
  - `src/main/resources/application-prod.yml`
  - `src/main/resources/db/migration/V14__create_product_qna_tables.sql` (신규)
  - `src/main/java/com/jangingmall/backend/image/presentation/ImageController.java`
  - `src/main/java/com/jangingmall/backend/member/application/MemberAccess.java`
  - `src/test/java/com/jangingmall/backend/image/presentation/ImageControllerTest.java`
  - `src/test/java/com/jangingmall/backend/member/application/MemberAccessTest.java`
  - `src/main/java/com/jangingmall/backend/member/infrastructure/DastLoginLockExemption.java` (신규)
  - `src/test/java/com/jangingmall/backend/member/infrastructure/DastLoginLockExemptionTest.java` (신규)
  - `src/test/java/com/jangingmall/backend/member/application/MemberAuthenticationServiceTest.java`
  - `src/test/java/com/jangingmall/backend/member/presentation/MemberControllerTest.java`
  - `src/test/java/com/jangingmall/backend/global/exception/GlobalExceptionHandlerTest.java` (신규)
- 작업 요약: 로그인 잠금 응답 403→429, 임계치/잠금시간 설정화, 예외 로그에 method/URI/status 추가, DAST 계정·IP(DART_IP_LIST) 잠금 기한 면제(~2026-10-05)
- 시작일: 2026-09-30

### Claude-현재세션 (ai-image-fetch-fail-fast)
- 브랜치: `fix/ai-image-fetch-fail-fast`
- 상태: 완료
- 접근 파일:
  - `src/main/java/com/jangingmall/backend/content/domain/AiImageFetchException.java` (신규)
  - `src/main/java/com/jangingmall/backend/content/infrastructure/RestAiContentClient.java`
  - `src/main/java/com/jangingmall/backend/content/application/GenerationAsyncExecutor.java`
  - `src/test/java/com/jangingmall/backend/content/infrastructure/RestAiContentClientTest.java`
  - `src/test/java/com/jangingmall/backend/content/application/GenerationAsyncExecutorTest.java`
- 작업 요약: AI 상품 이미지 다운로드 실패(URL 형식·HTTP 상태·Content-Type) 시 빈 데이터 대신 즉시 실패 처리, 재시도 제외
- 시작일: 2026-09-30

## 완료된 작업 (참고용)

(없음)
