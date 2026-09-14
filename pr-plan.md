# 강정훈 R&R — 브랜치별 PR 구현 계획

> 기준일: 2026-09-03  
> 담당 도메인: 상품(product), 콘텐츠(content), 챗봇(chatbot), 알림(notification — 백로그)  
> 전제: develop 기준 greenfield (product/content/chatbot 모두 package-info.java만 존재)

---

## 브랜치 순서 (의존성 기준)

```
feat/product-core          ← 1순위 (product 엔티티 — 모든 도메인의 기반)
feat/product-category      ← 2순위 (카테고리 조회 — product-core 이후)
feat/content-interview     ← 3순위 (취재 데이터 — product 존재 전제)
feat/content-ai-generation ← 4순위 (AI 생성 — interview 데이터 필요)
feat/content-edit          ← 5순위 (편집 — generation 이후)
feat/content-lifecycle     ← 6순위 (승인/반려/게시 — edit 이후)
feat/product-wish          ← 7순위 P1 (찜 — product-core 이후)
feat/product-qna           ← 8순위 P1 (문의/답변 — product-core 이후)
feat/product-review        ← 9순위 P2 (후기 — 주문 완료 조건)
feat/chatbot               ← 10순위 P2
feat/notification          ← 11 p2
```

---

## P0 — 핵심 MVP

### feat/product-core

**API 목록**

| 메서드 | 경로 | 설명 |
|---|---|---|
| POST | `/api/products` | 상품 등록 |
| GET | `/api/products/me` | 내 상품 목록 (장인) |
| PATCH | `/api/products/{productId}` | 상품 수정 |
| DELETE | `/api/products/{productId}` | 상품 삭제 |
| PATCH | `/api/products/{productId}/status` | 상태 변경 |
| GET | `/api/products` | 상품 전체 목록 (소비자) |
| GET | `/api/products/{productId}` | 상품 상세 |

**ERD 테이블:** `product`, `category`, `subcategory`

**도메인 레이어 구현 범위**

- Domain: `Product` 엔티티, `ProductStatus` enum (DRAFT/ON_SALE/SOLD_OUT/HIDDEN), `ProductRepository` 인터페이스
- Application: `ProductService`, `ProductCommand`, `ProductResponse`
- Infrastructure: `JpaProductRepository`
- Presentation: `ProductController`, `ProductRequest`, `ProductResponse`

**테스트 전략**

| 테스트 유형 | 대상 |
|---|---|
| JunitTest | ProductStatus 상태 전이 규칙, 가격·재고 원시값 포장 검증 |
| JunitExceptionTest | 유효하지 않은 상태 전이, 권한 없는 수정 시도 |
| IntegrationTest | 상품 등록 → 조회 → 수정 → 상태 변경 → 삭제 전체 흐름 |
| IntegrationExceptionTest | 존재하지 않는 상품 조회, 타 장인 상품 수정 시도 |
| REST Docs | 전체 7개 엔드포인트 100% 문서화 |

---

### feat/product-category

**API 목록**

| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/api/products/categories` | 카테고리 목록 |
| GET | `/api/products/subcategories` | 서브카테고리 목록 |
| GET | `/api/products/materials` | 소재 목록 |
| GET | `/api/products/categories/main` | 메인 카테고리 (P1) |

**ERD 테이블:** `category`, `subcategory`

**선행 조건:** `feat/product-core` 머지 완료

**도메인 레이어 구현 범위**

- Domain: `Category`, `Subcategory` 엔티티는 product-core에서 생성 — 여기서는 조회 API만 추가
- Application: `CategoryQueryService`
- Presentation: `CategoryController`

**테스트 전략**

| 테스트 유형 | 대상 |
|---|---|
| IntegrationTest | 카테고리 조회 응답 구조 검증 |
| REST Docs | 4개 엔드포인트 100% 문서화 |

**Rest docs -> redocly 까지 완전 자동화**

---

### feat/content-interview

**API 목록**

| 메서드 | 경로 | 설명 |
|---|---|---|
| POST | `/api/content/products/{productId}/interview` | 취재 데이터 등록 |
| GET | `/api/content/products/{productId}/interview` | 취재 데이터 조회 |
| PATCH | `/api/content/products/{productId}/interview` | 취재 데이터 수정 |

**ERD 테이블:** `interview` (product 1:1)

**선행 조건:** `feat/product-core` 머지 완료

**도메인 레이어 구현 범위**

- Domain: `Interview` 엔티티, `InterviewRepository` 인터페이스
- Application: `InterviewService`, `InterviewCommand`, `InterviewResponse`
- Infrastructure: `JpaInterviewRepository`
- Presentation: `InterviewController`

**테스트 전략**

| 테스트 유형 | 대상 |
|---|---|
| JunitTest | 취재 데이터 필드 유효성 |
| IntegrationTest | 등록 → 조회 → 수정 흐름 |
| IntegrationExceptionTest | 존재하지 않는 상품 취재 데이터 등록 |
| REST Docs | 3개 엔드포인트 100% 문서화 |

---

### feat/content-ai-generation

**API 목록**

| 메서드 | 경로 | 설명 |
|---|---|---|
| POST | `/api/content/products/{productId}/generations` | AI 생성 요청 |
| GET | `/api/content/products/{productId}/generations/{generationId}` | 생성 결과 폴링 |

**ERD 테이블:** `content`, `content_block`, `content_edit_history`

**선행 조건:** `feat/content-interview` 머지 완료

**도메인 레이어 구현 범위**

- Domain: `Content` 엔티티 (`@Version` 낙관적 락), `ContentStatus` enum (DRAFT/PENDING_REVIEW/APPROVED/REJECTED/PUBLISHED), `ContentBlock` 엔티티, `ContentEditHistory` 엔티티, `EditedByType` enum (AI/ARTISAN)
- Application: `ContentGenerationService` — Modal AI 호출, HTML → JSON 변환
- Infrastructure: `ModalAiClient` (AI 서버 연동), `JpaContentRepository`
- Presentation: `ContentGenerationController`

**주의사항**

- Modal AI 전송 허용 데이터: 상품 사진 + 설명 프롬프트만 허용. 개인정보·주문정보·접근 토큰 전송 금지.
- BE가 AI HTML 응답을 파싱 → `ContentBlock` JSON 배열로 변환하여 FE에 전달
- 비동기 생성 — POST는 `generationId` 반환, GET으로 폴링

**테스트 전략**

| 테스트 유형 | 대상 |
|---|---|
| JunitTest | HTML → ContentBlock 변환 로직, ContentStatus 상태 전이 |
| JunitExceptionTest | AI 응답 파싱 실패, 타임아웃 |
| IntegrationTest | 생성 요청 → 폴링 → DRAFT 상태 확인 |
| IntegrationExceptionTest | interview 없는 상품 생성 요청 |
| REST Docs | 2개 엔드포인트 100% 문서화 |

**관측 의무 (observability.md 준수)**

- AI 호출 실패/타임아웃 → catch 후 무시 금지, 로그 + 상태값으로 드러낼 것
- poison 메시지(파싱 불가 HTML) 격리 처리 경로 코드상 추적 가능해야 함

---

### feat/content-edit

**API 목록**

| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/api/content/products/{productId}/contents` | 현재 콘텐츠 조회 |
| PATCH | `/api/content/products/{productId}/contents/{contentId}` | 콘텐츠 일괄 수정 |
| PATCH | `/api/content/products/{productId}/contents/{contentId}/blocks/{blockOrder}` | 단건 블록 수정 (CONTENT-012) |
| GET | `/api/content/products/{productId}/contents/versions` | 버전 이력 조회 |

**ERD 테이블:** `content`, `content_block`, `content_edit_history`

**선행 조건:** `feat/content-ai-generation` 머지 완료

**핵심 명세 (CONTENT-012)**

```
imageUrl (string, nullable) — 이미지 원본 URL (tag=img일 때만)
→ BE: S3 처리 후 imageVariants [{url, width, height, format}×3] 반환

editedBy: AI | ARTISAN
```

**도메인 레이어 구현 범위**

- Application: `ContentEditService` — 일괄/단건 블록 수정, 낙관적 락 충돌 처리, `editedBy=ARTISAN` 이력 기록
- Infrastructure: `S3ImageProcessor` (presigned URL → imageVariants 변환)

**테스트 전략**

| 테스트 유형 | 대상 |
|---|---|
| JunitTest | imageUrl → imageVariants 변환, editedBy 이력 기록 |
| JunitExceptionTest | 낙관적 락 충돌 (OptimisticLockException) |
| IntegrationTest | 일괄 수정 → 버전 이력 생성, 단건 블록 수정 |
| REST Docs | 4개 엔드포인트 100% 문서화 |

---

### feat/content-lifecycle

**API 목록**

| 메서드 | 경로 | 설명 |
|---|---|---|
| POST | `/api/content/products/{productId}/contents/{contentId}/approve` | 승인 |
| POST | `/api/content/products/{productId}/contents/{contentId}/reject` | 반려 |
| POST | `/api/content/products/{productId}/publish` | 게시 |

**선행 조건:** `feat/content-edit` 머지 완료

**상태 전이:** DRAFT → PENDING_REVIEW → APPROVED → PUBLISHED / REJECTED

**도메인 레이어 구현 범위**

- Application: `ContentLifecycleService` — 상태 전이 규칙 강제, 역할 기반 권한 검증 (ADMIN만 approve/reject)

**테스트 전략**

| 테스트 유형 | 대상 |
|---|---|
| JunitTest | 상태 전이 허용/불허 규칙 전체 |
| JunitExceptionTest | 권한 없는 승인 시도, 잘못된 상태에서 전이 시도 |
| IntegrationTest | DRAFT → PENDING_REVIEW → APPROVED → PUBLISHED 전체 흐름 |
| IntegrationExceptionTest | REJECTED 상태에서 재게시 시도 |
| REST Docs | 3개 엔드포인트 100% 문서화 |

---

## P1 — 1차 고도화

### feat/product-wish

**API 목록**

| 메서드 | 경로 | 설명 |
|---|---|---|
| POST | `/api/products/{productId}/wish` | 찜 등록 |
| DELETE | `/api/products/{productId}/wish` | 찜 취소 |

**ERD 테이블:** `wishlist` (UNIQUE: member_id, product_id)

**선행 조건:** `feat/product-core` 머지 완료

**테스트 전략**

| 테스트 유형 | 대상 |
|---|---|
| JunitExceptionTest | 중복 찜 등록 (UNIQUE 위반) |
| IntegrationTest | 찜 등록 → 취소 흐름 |
| REST Docs | 2개 엔드포인트 100% 문서화 |

---

### feat/product-qna

**API 목록**

| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/api/products/{productId}/questions` | 문의 목록 |
| POST | `/api/products/{productId}/questions` | 문의 등록 |
| POST | `/api/products/questions/{questionId}/answer` | 답변 등록 (장인) |

**ERD 테이블:** `product_question` (answer nullable)

**선행 조건:** `feat/product-core` 머지 완료

**테스트 전략**

| 테스트 유형 | 대상 |
|---|---|
| JunitExceptionTest | 비장인 계정의 답변 등록 시도 |
| IntegrationTest | 문의 등록 → 답변 등록 → 목록 조회 |
| REST Docs | 3개 엔드포인트 100% 문서화 |

---

## P2 — 2차 고도화

### feat/product-review

**API 목록**

| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/api/products/{productId}/reviews` | 후기 목록 |
| POST | `/api/products/{productId}/reviews` | 후기 등록 |

**ERD 테이블:** `product_review` (order_item_id FK — 구매 완료 조건)

**선행 조건:** `feat/product-core` 머지 완료, 주문 도메인(order_item) 구현 완료

**주의:** 주문 완료 여부 검증은 주문 도메인과 연동 필요 — 담당자 간 인터페이스 협의 필수

**테스트 전략**

| 테스트 유형 | 대상 |
|---|---|
| JunitExceptionTest | 미구매 상품 후기 등록 시도 |
| IntegrationTest | 후기 등록 → 목록 조회 |
| REST Docs | 2개 엔드포인트 100% 문서화 |

---

### feat/chatbot

**API 목록**

| 메서드 | 경로 | 설명 |
|---|---|---|
| POST | `/api/chatbot/sessions` | 세션 생성 |
| POST | `/api/chatbot/sessions/{sessionId}/messages` | 메시지 전송 |
| GET | `/api/chatbot/sessions/{sessionId}/messages` | 메시지 조회 |
| DELETE | `/api/chatbot/sessions/{sessionId}` | 세션 삭제 |

**선행 조건:** AI 팀과 챗봇 API 스펙 사전 합의

**주의사항 (Modal AI 보안 제약)**

- 자연어 챗봇 입력·대화 이력, 개인정보, 반품 증거, 주문/계정 정보, 접근 토큰 → Modal 전송 절대 금지
- BE는 프롬프트 필터링 레이어에서 금지 항목 제거 후 전달

**테스트 전략**

| 테스트 유형 | 대상 |
|---|---|
| JunitTest | 프롬프트 필터링 로직 |
| IntegrationTest | 세션 생성 → 메시지 전송 → 조회 → 삭제 |
| REST Docs | 4개 엔드포인트 100% 문서화 |

**관측 의무**

- AI 응답 실패/타임아웃 → catch 후 무시 금지

---

## 백로그 

### feat/notification

> notification 도메인은 스켈레톤 코드 이미 존재 (NotificationService, NotificationController, Notification 엔티티, NotificationRepository, NotificationStatus). 기존 코드 파악 후 구현.

**API 목록**

| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/api/notifications` | 알림 목록 |
| GET | `/api/notifications/unread-count` | 미읽음 수 |
| PATCH | `/api/notifications/{notificationId}` | 알림 읽음 처리 |
| PATCH | `/api/notifications/read-all` | 전체 읽음 처리 |
| GET | `/api/notifications/stream` | SSE 실시간 알림 |

**관측 의무 (SSE)**

- SSE 연결 유실/재연결 실패 → 조용한 실패 금지, 연결 상태 로그 필수
- 이벤트 전송 실패 경로 코드상 추적 가능해야 함

---

## 모니터링 계획

### 인프라/보안팀 확인 항목

> 아래 항목은 백엔드 단독 결정 불가 — 인프라·보안팀과 사전 합의 필요

| 항목 | 내용 | 담당 |
|---|---|---|
| Prometheus scrape 설정 | `/actuator/prometheus` (port 9090), interval 15s | 인프라팀 |
| Grafana 대시보드 프로비저닝 | 공통 JVM 대시보드 + 도메인별 커스텀 패널 | 인프라팀 |
| CloudWatch 연동 여부 | 솔루션 기능에 따라 선택적 사용 | 인프라팀 |
| WAF/ALB 접근 로그 수집 | ALB access log → S3 → CloudWatch | 보안팀 |
| 알림 규칙 (AlertManager) | 임계치 초과 시 PagerDuty/Slack 라우팅 | 인프라팀 |
| Grafana 계정·권한 | 개발자 read-only vs 운영자 write 분리 | 인프라팀 |
| 보안 이벤트 모니터링 | 인증 실패 급증, 비정상 트래픽 패턴 감지 임계치 | 보안팀 |

---

### 백엔드 구현 항목 (도메인별)

#### 공통 — 모든 도메인

| 메트릭 | 구현 방법 | 수집 목적 |
|---|---|---|
| HTTP 요청 수·지연 | Spring Boot Actuator 기본 제공 (`http.server.requests`) | SLA 추적 |
| JVM 힙·GC | Micrometer 기본 제공 | OOM 사전 감지 |
| DB 커넥션 풀 | HikariCP 메트릭 (`hikaricp.*`) | 커넥션 고갈 감지 |
| 에러율 | `http.server.requests` status=5xx 필터링 | 장애 감지 |

#### feat/product-core — 상품 도메인

| 메트릭 | 구현 | 설명 |
|---|---|---|
| 상품 등록 수 | `Counter("product.created")` | 일별 신규 상품 추이 |
| 상태별 상품 수 | `Gauge("product.count", tag=status)` | DRAFT/ON_SALE/SOLD_OUT/HIDDEN 분포 |
| 상품 조회 응답시간 | `@Timed("product.query")` | 목록·상세 쿼리 성능 |

#### feat/content-ai-generation — AI 생성 도메인

| 메트릭 | 구현 | 설명 |
|---|---|---|
| AI 생성 요청 수 | `Counter("content.generation.requested")` | 사용량 추적 |
| AI 생성 성공/실패 | `Counter("content.generation.result", tag=status)` | 실패율 모니터링 |
| AI 응답 대기시간 | `Timer("content.generation.latency")` | SLA 관리 (timeout 기준) |
| HTML 파싱 실패 | `Counter("content.generation.parse_error")` | poison 메시지 감지 |

> **보안팀 확인:** AI 서버(Modal) 호출 로그에 상품 사진 URL 외 개인정보 포함 여부 주기적 감사 필요

#### feat/content-edit — 콘텐츠 편집 도메인

| 메트릭 | 구현 | 설명 |
|---|---|---|
| 낙관적 락 충돌 | `Counter("content.edit.optimistic_lock_conflict")` | 동시 편집 충돌 빈도 |
| S3 업로드 실패 | `Counter("content.edit.s3_error")` | 이미지 처리 실패 감지 |

#### feat/chatbot — 챗봇 도메인

| 메트릭 | 구현 | 설명 |
|---|---|---|
| 활성 세션 수 | `Gauge("chatbot.session.active")` | 동시 사용자 수 |
| 메시지 처리 실패 | `Counter("chatbot.message.error")` | AI 응답 실패율 |
| 프롬프트 필터링 차단 | `Counter("chatbot.prompt.blocked")` | 보안 제약 위반 시도 횟수 |

> **보안팀 확인:** 프롬프트 필터링 차단 이벤트 — 임계치 초과 시 보안팀 알림 연동 필요

#### feat/notification — 알림 도메인 (백로그)

| 메트릭 | 구현 | 설명 |
|---|---|---|
| SSE 활성 연결 수 | `Gauge("notification.sse.connections")` | 연결 과부하 감지 |
| SSE 연결 유실 | `Counter("notification.sse.disconnect")` | 비정상 종료 추이 |
| 알림 전송 실패 | `Counter("notification.send.error")` | 조용한 실패 방지 |

---

### Grafana 대시보드 구성 (인프라팀 협의안)

```
Row 1: 서비스 전체 현황
  - HTTP 요청률 / 에러율 / P99 응답시간
  - JVM 힙 사용률 / GC 횟수
  - DB 커넥션 풀 사용률

Row 2: 도메인별 비즈니스 메트릭
  - 상품: 상태별 수, 등록 추이
  - 콘텐츠: AI 생성 성공률, 편집 충돌 횟수
  - 챗봇: 활성 세션, 필터링 차단 이벤트
  - 알림: SSE 연결 수, 전송 실패율

Row 3: 외부 연동 상태
  - Modal AI: 응답시간, 실패율
  - Delivery Tracker: 응답시간, 실패율
  - S3: 업로드 실패율
```

---

## 공통 적용 규칙 (전 브랜치)

- 모든 API: `@Valid` 1차 유효성 검사, 비즈니스 규칙은 Service/Domain에서 검증
- `@Transactional(readOnly = true)` 조회 메서드에 명시적 선언
- 매직 넘버/문자열 → Enum 또는 상수로 관리
- 코드·라인 커버리지 95% 이상, REST API 100% 문서화
- 비동기/외부 호출 실패 경로 테스트 포함 (observability.md 준수)
- PR 내 테스트 포함 — 별도 테스트 브랜치 없음
- 도메인별 커스텀 메트릭(`@Timed`/`Counter`/`Gauge`)은 해당 브랜치 PR에 함께 포함
