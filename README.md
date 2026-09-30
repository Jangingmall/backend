# 미담 (Midam) — 장인 공예 커머스 백엔드

> 국가무형유산 장인의 작품을 소비자와 연결하는 B2C 공예 전문 커머스 플랫폼

[![Java](https://img.shields.io/badge/Java-25-007396?logo=openjdk)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.0.3-6DB33F?logo=springboot)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-18-4169E1?logo=postgresql)](https://www.postgresql.org/)
[![Redis](https://img.shields.io/badge/Redis-8-DC382D?logo=redis)](https://redis.io/)

---

## 프로젝트 개요

공예품 직접 판매 의존도 71.7%, 장인 평균 연령 75세 — 온라인 판로를 갖지 못한 국가 공인 장인을 위한 커머스 플랫폼입니다.
소비자 입장에서도 기존 플랫폼에는 장인의 자격 등급을 식별할 수단이 없었습니다. 미담은 장인 인증 등급을 표면화하고, AI 기반 개인화 추천으로 공예품 구매 경험을 개선합니다.

| 구분 | 수치 |
|---|---|
| 장인 | 52명 |
| 상품 | 832개 / 카테고리 6종·서브카테고리 27종 |
| API 엔드포인트 | 101개 (전 엔드포인트 REST Docs 문서화) |
| 동시성 시나리오 | 16개 설계·문서화 |
| 소스 파일 | Java 347개 / 테스트 84개 |

---

## 담당 영역 (강정훈)

> 팀원: 강정훈 · 유창민 (2인 백엔드)

| 도메인 | 엔드포인트 | 핵심 책임 |
|---|:---:|---|
| **상품** | 16 | 카탈로그, 3계층 옵션, 찜, 문의, 후기, 공개 조회 |
| **콘텐츠** | 11 | AI 상세페이지 생성 파이프라인, 블록 편집, ADMIN·장인 승인 워크플로우 |
| **챗봇** | 4 | AI 서버 연동, 세션 관리, 자연어 상품 추천 |
| **알림** | 4 | SSE 기반 실시간 알림, Redis Stream PEL 재처리 |

---

## 기술 스택

| 구분 | 기술 |
|---|---|
| Language / Runtime | Java 25, Virtual Threads |
| Framework | Spring Boot 4.0.3, Spring Security, Spring Data JPA |
| ORM | JPA + QueryDSL (성능 필요 시 Native Query) |
| Database | PostgreSQL 18, Redis 8 |
| Auth | JWT (Access 30분 / Refresh 7일 HttpOnly Cookie), OAuth2 (Kakao · Naver) |
| API 문서 | Spring REST Docs → OpenAPI → Redocly (빌드 자동화) |
| 결제 | 토스페이먼츠 |
| 인프라 | AWS EC2 · ALB · S3, nginx, GitHub Actions |

---

## 아키텍처

### 설계 원칙

- **DDD** 기반 도메인 패키징 — `presentation / application / domain / infrastructure` 4계층
- **클린 아키텍처** — 도메인 간 참조는 ID만 허용, Entity 직접 참조 금지
- **모놀리식 단일 배포** — 서비스 간 경계는 패키지로 분리, 브로커 없이 `ApplicationEvent` 활용

### 배포 구성

```
Client
  └── HTTPS ──► AWS ALB (TLS 종료)
                    └── HTTP ──► EC2 · nginx (리버스 프록시)
                                    └── HTTP ──► Spring Boot jar (Tomcat 내장)
```

### 패키지 구조

```
com.jangingmall.backend
├── global/          공통 설정, 예외, 보안, dev 유틸
├── member/          회원·장인·OAuth2·주소 (유창민)
├── payment/         장바구니·주문·결제·환불 (유창민)
├── admin/           관리자 (유창민)
├── image/           S3 Presigned URL (유창민)
├── product/         상품·옵션·찜·문의·후기 (강정훈)
├── content/         AI 상세페이지 생성·검토·게시 (강정훈)
├── chatbot/         AI 자연어 추천 세션 (강정훈)
├── notification/    SSE 실시간 알림 (강정훈)
└── revalidate/      FE ISR 재검증 이벤트
```

---

## 핵심 구현

### 1. AI 상세페이지 자동 생성 파이프라인

장인이 취재 데이터(제작과정·소재·관리법)를 입력하면 AI가 상품 상세페이지 블록을 자동 생성합니다. ADMIN 팩트체크 → 장인 최종 승인 → 게시까지 4단계 워크플로우를 구현했습니다.

```
취재 데이터 입력
  └──► AI 생성 요청 → PROCESSING → COMPLETED
            └──► ADMIN 팩트체크 승인
                      └──► 장인 최종 승인
                                └──► publish → FE ISR 재검증 이벤트 발행
```

- 낙관적 락(`content.version`)으로 승인·반려 동시 요청 충돌 방지
- 콘텐츠 이력(`content_edit_history`)으로 전 버전 보관
- AI 출력(HTML)을 BE가 블록 단위(`{tag, text, imageUrl}`)로 파싱·저장, FE는 블록 단위 수신

### 2. AI 챗봇 자연어 상품 추천

```
소비자 자연어 입력 ("엄마 환갑 선물 5만원대 고급스러운 걸로")
  └──► [백엔드] 세션 저장 + POST /ai/chat 호출
            └──► [AI 서버 / FastAPI]
                      ① 의도분류·조건추출 (Qwen3)
                      ② 쿼리 임베딩 (KURE-v1 1024차원)
                      ③ 하이브리드 검색 (Vector + BM25 + RRF)
                      ④ 인증 등급 가중 랭킹
                      ⑤ 근거 기반 추천이유 생성 (Qwen3)
  └──► [백엔드] 상품 카드 조립 후 FE 응답
```

- AI 호출 실패·타임아웃(30초) → 최대 2회 재시도 후 fallback 반환
- BE는 소비자 원문 그대로 전달 — 가공 금지로 의도분류 정확도 보호

### 3. 동시성 제어 (16개 시나리오)

DB 트랜잭션으로 해결 가능한 범위를 먼저 확정하고, 분산 락은 필수 지점에만 제한 적용하는 전략으로 설계했습니다.

| 전략 | 적용 시나리오 |
|---|---|
| 조건부 원자적 UPDATE | 재고 차감 (`stock >= qty` WHERE 조건으로 음수 방지) |
| DB UNIQUE 제약 | 찜 중복 방지 `UNIQUE(member_id, product_id)`, Presigned URL 단일 소비 |
| 낙관적 락 (`@Version`) | 콘텐츠 승인·반려 동시 처리, 결제 취소 중복 요청 |
| Partial Unique Index | 장인 가입 중복 신청 — `PENDING` 상태에서만 유일 제약 |
| UPSERT | 최근 본 상품 중복 기록 (`ON CONFLICT DO UPDATE`) |
| 멱등키 | PG사 콜백 재전송, 주문 중복 생성 방지 |

전체 시나리오: [`docs/장인몰_동시성_처리_전략.csv`](docs/장인몰_동시성_처리_전략.csv)

### 4. 실시간 알림 (SSE)

Redis Stream(PEL 재처리) + Redis Pub/Sub(멀티 인스턴스 브로드캐스트) + Spring MVC `SseEmitter` 조합으로 구현했습니다.

| 레이어 | 설정값 | 이유 |
|---|---|---|
| ALB idle timeout | 3600s | heartbeat(30s) 120배 여유 |
| nginx `proxy_read_timeout` | 3600s + `proxy_buffering off` | SSE 버퍼링 방지 |
| `spring.mvc.async.request-timeout` | `-1` | Tomcat 기본 30s 강제종료 방지 |
| `SseEmitter` timeout | `-1L` | 무제한 |
| Redis `sse:online` TTL | 90s | heartbeat마다 갱신 |

### 5. 장인 인증 등급 체계

장인의 국가 공인 자격을 4단계로 표면화하여 소비자 신뢰 지표로 활용합니다.

| 등급 | 설명 |
|---|---|
| `NATIONAL_INTANGIBLE_HERITAGE` | 국가무형유산 보유자 |
| `MASTER_CRAFTSMAN` | 전승교육사 |
| `SENIOR_CRAFTSMAN` | 이수자 |
| `YOUNG_CRAFTSMAN` | 일반 |

온보딩은 서류 접수 → 수공정성 심사 → 디지털 변환 지원 → 주문제작 연동 4단계로 구성되며, 각 단계는 `PENDING / IN_PROGRESS / COMPLETED / FAILED` 상태로 추적됩니다.

---

## API 도메인 구조

**백엔드**: `https://api.midam.store` / **스테이징**: `https://api.stg.midam.store`  
전체 명세: [`docs/장인몰_API_명세_v1.csv`](docs/장인몰_API_명세_v1.csv)

| 도메인 | 기본 경로 | 엔드포인트 수 | 담당 |
|---|---|:---:|---|
| 회원 | `/api/member/**` | 36 | 유창민 |
| 결제 | `/api/payments/**` | 17 | 유창민 |
| 관리자 | `/api/admin/**` | 6 | 유창민 |
| 이미지 | `/api/images/**` | 3 | 유창민 |
| 상품 | `/api/products/**` | 16 | 강정훈 |
| 콘텐츠 | `/api/content/**` | 11 | 강정훈 |
| 알림 | `/api/notifications/**` | 4 | 강정훈 |
| 챗봇 | `/api/chatbot/**` | 4 | 강정훈 |

### 인증 레벨

| 레벨 | 설명 |
|---|---|
| `Public` | 인증 불필요 |
| `Public (게스트)` | 비회원은 쿠키 `guestCartId`로 식별 |
| `Authenticated` | JWT Bearer 필요 |
| `USER / ARTISAN / ADMIN` | 역할 기반 접근 제어 |

---

## 테스트 전략

- **단위 테스트** — 비즈니스 규칙 검증, GIVEN-WHEN-THEN 패턴
- **통합 테스트** — 실제 DB 연동 (Mock 금지)
- **E2E 테스트** — 전 엔드포인트 100% 커버
- 코드·라인 커버리지 95% 이상 목표
- REST Docs 기반 API 문서 자동화 — 빌드 시 OpenAPI 생성 → Redocly 퍼블리시

---

## 공통 응답 포맷

```json
{ "success": true,  "status": 200, "data": {} }
{ "success": false, "status": 400, "errorCode": "BUSINESS_RULE_VIOLATION" }
```

오류 코드: `INVALID_INPUT` · `UNAUTHORIZED` · `FORBIDDEN` · `NOT_FOUND` · `CONFLICT` · `BUSINESS_RULE_VIOLATION` · `CONCURRENT_UPDATE`

---

## 로컬 실행

```bash
# Redis 실행
docker run -d --name redis -p 6379:6379 redis:8

# 빌드 (REST Docs → OpenAPI → redoc.html 생성 포함)
./gradlew build

# 실행 (local 프로파일 — H2 인메모리 DB 자동 구성)
./gradlew bootRun
```

로컬 개발용 JWT 즉시 발급: `POST /dev/token?role=ARTISAN` (local 프로파일 전용)

---

## 팀원

| **강정훈** | **유창민** |
|:---:|:---:|
| [<img src="https://avatars.githubusercontent.com/u/105915960?v=4" width=80>](https://github.com/JHkoder)<br/>[@JHkoder](https://github.com/JHkoder) | [<img src="https://avatars.githubusercontent.com/u/268832835?v=4" width=80>](https://github.com/dnwn3295-lgtm)<br/>[@dnwn3295-lgtm](https://github.com/dnwn3295-lgtm) |
| 상품 · 콘텐츠 · 챗봇 · 알림 | 회원 · 결제 · 관리자 · 이미지 |

---

## 문서 목록

| 문서 | 경로 |
|---|---|
| API 명세 (전체) | [`docs/장인몰_API_명세_v1.csv`](docs/장인몰_API_명세_v1.csv) |
| ERD 설계 | [`docs/ERD_설계.md`](docs/ERD_설계.md) |
| 동시성 처리 전략 | [`docs/장인몰_동시성_처리_전략.csv`](docs/장인몰_동시성_처리_전략.csv) |
| 서버 아키텍처 | [`docs/서버_아키텍처.md`](docs/서버_아키텍처.md) |
| API 공통 규칙 | [`docs/API_공통규칙.md`](docs/API_공통규칙.md) |
| 인증 정책 계약 | [`docs/PHASE2-2_인증_정책_계약서.md`](docs/PHASE2-2_인증_정책_계약서.md) |
| AI 통합 계약 | [`docs/PHASE2-3_AI_통합_계약서.md`](docs/PHASE2-3_AI_통합_계약서.md) |
| 배포 환경변수 계약 | [`docs/PHASE2-4_배포_환경변수_계약서.md`](docs/PHASE2-4_배포_환경변수_계약서.md) |
