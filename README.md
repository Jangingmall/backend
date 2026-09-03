# 장인몰 백엔드 — 미담 (Midam)

> 국가무형유산 장인의 공예 작품을 소비자와 연결하는 B2C 공예 전문 커머스 플랫폼

[![Java](https://img.shields.io/badge/Java-25-007396?logo=openjdk)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.0.3-6DB33F?logo=springboot)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-18-4169E1?logo=postgresql)](https://www.postgresql.org/)
[![Redis](https://img.shields.io/badge/Redis-8-DC382D?logo=redis)](https://redis.io/)

---

## 팀원

| **강정훈** | **유창민** |
|:---:|:---:|
| [<img src="https://avatars.githubusercontent.com/u/105915960?v=4" width=100>](https://github.com/JHkoder)<br/>[@JHkoder](https://github.com/JHkoder) | [<img src="https://avatars.githubusercontent.com/u/268832835?v=4" width=100>](https://github.com/dnwn3295-lgtm)<br/>[@dnwn3295-lgtm](https://github.com/dnwn3295-lgtm) |
| 상품 · 콘텐츠 · 챗봇 · 알림 | 회원 · 결제 · 관리자 · 이미지 |

---

## 프로젝트 개요

### 왜 만드는가

| 대상 | 문제 |
|---|---|
| 공급자 (장인) | 온라인 판로 부재 — 공예품 직접 판매 의존 71.7%, 장인 평균 연령 75세 |
| 수요자 (소비자) | 국가 공인 장인 여부 판단 불가 — 기존 플랫폼에 자격 식별 수단 없음 |

### 규모

- 장인 **52명**, 상품 **832개**, 카테고리 **6종 / 27 서브카테고리**
- 엔드포인트 **101개**, 동시성 시나리오 **16개** 설계·문서화

### MVP 목표일

**2026-09-21**

---

## 기술 스택

| 구분 | 기술 |
|---|---|
| Language / Runtime | Java 25, Virtual Threads |
| Framework | Spring Boot 4.0.3, Spring Security, Spring Data JPA |
| ORM | JPA + QueryDSL (성능 이슈 시 Native Query) |
| Database | PostgreSQL 18, Redis 8 |
| Auth | JWT (Access 30분 / Refresh 7일 HttpOnly Cookie), OAuth2 (Kakao · Google) |
| API 문서 | Spring REST Docs → OpenAPI → Redocly |
| 결제 | 토스페이먼츠 |
| 인프라 | AWS EC2 · ALB · S3, nginx, GitHub Actions |

---

## 아키텍처

### 설계 원칙

- **DDD** 기반 도메인 패키징 (`presentation / application / domain / infrastructure`)
- **클린 아키텍처** — 도메인 간 참조는 ID만 허용, 엔티티 직접 참조 금지
- **모놀리식 단일 배포** — 서비스 간 경계는 패키지로 분리, 브로커 없이 `ApplicationEvent` 활용

### 배포 구성

```
Client
  └── HTTPS ──► AWS ALB (TLS 종료)
                    └── HTTP ──► EC2 · nginx (리버스 프록시)
                                    └── HTTP ──► Spring Boot jar (Tomcat 내장)
```

### AI 챗봇 흐름

```
소비자 자연어 입력
  └──► [백엔드] 세션 저장 + POST /ai/chat 호출
            └──► [AI 서버 / FastAPI]
                      ① 의도분류 · 조건추출 (Qwen3)
                      ② 쿼리 임베딩 (KURE-v1 1024차원)
                      ③ 하이브리드 검색 (Vector + BM25 + RRF)
                      ④ 인증 등급 가중 랭킹
                      ⑤ 근거 기반 추천이유 생성 (Qwen3)
  └──► [백엔드] 상품 카드 조립 후 FE 응답
```

---

## API 도메인 구조

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

## 핵심 설계 결정

### 동시성 제어 (16개 시나리오)

전략별로 구분하여 DB 트랜잭션만으로 해결 가능한 범위를 먼저 확정하고, 분산 락은 필수 지점에만 제한 적용.

| 전략 | 적용 시나리오 |
|---|---|
| 조건부 원자적 UPDATE | 재고 차감 (`stock >= qty` WHERE 조건으로 음수 방지) |
| DB UNIQUE 제약 | 찜 중복 방지 `UNIQUE(member_id, product_id)`, Presigned URL 단일 소비 |
| 낙관적 락 (`@Version`) | 콘텐츠 승인/반려 동시 처리, 결제 취소 중복 요청 |
| Partial Unique Index | 장인 가입 중복 신청 — `PENDING` 상태에서만 유일 제약 |
| UPSERT | 최근 본 상품 중복 기록 (`ON CONFLICT DO UPDATE`) |
| 멱등키 | PG사 콜백 재전송, 주문 중복 생성 방지 |

전체 시나리오: [`docs/장인몰_동시성_처리_전략.csv`](docs/장인몰_동시성_처리_전략.csv)

### AI 상세페이지 자동 생성 파이프라인

장인이 취재 데이터(제작과정·소재·관리법)를 입력하면 AI가 상품 상세페이지 블록(`h2 / p / img / video`)을 자동 생성하고, ADMIN 검수 → 장인 최종 승인 → 게시 단계를 거쳐 FE ISR 캐시를 재검증한다.

```
취재 데이터 입력
  └──► AI 생성 요청 → PROCESSING → COMPLETED
            └──► ADMIN 팩트체크 승인 (factCheckConfirmed)
                      └──► 장인 최종 승인
                                └──► 게시 (publish) → FE ISR 재검증 이벤트 발행
```

- 낙관적 락(`content.version`)으로 승인/반려 동시 요청 충돌 방지
- 콘텐츠 이력(`content_edit_history`) 전 버전 보관

### 실시간 알림 (SSE) — MVP 백로그

Redis Stream(PEL 재처리) + Redis Pub/Sub(멀티 인스턴스 브로드캐스트) + Spring MVC `SseEmitter` 조합. 30초 heartbeat 기반 연결 유지.

| 레이어 | 설정값 |
|---|---|
| ALB idle timeout | 3600s |
| nginx `proxy_read_timeout` | 3600s + `proxy_buffering off` |
| `spring.mvc.async.request-timeout` | `-1` (무제한) |
| `SseEmitter` timeout | `-1L` (무제한) |
| Redis `sse:online:{memberId}` TTL | 90s (heartbeat마다 갱신) |

### 장인 온보딩 파이프라인 (4단계)

```
서류 접수 → 수공정성 심사 → 디지털 변환 지원 → 주문제작 시스템 연동
```

각 단계는 `PENDING / IN_PROGRESS / COMPLETED / FAILED` 상태로 추적. 수공정성 심사 완료 시 `qualificationTier` 확정 필수.

| 등급 | 설명 |
|---|---|
| `NATIONAL_INTANGIBLE_HERITAGE` | 국가무형유산 보유자 |
| `MASTER_CRAFTSMAN` | 전승교육사 |
| `SENIOR_CRAFTSMAN` | 이수자 |
| `YOUNG_CRAFTSMAN` | 일반 |

---

## 테스트 전략

- **단위 테스트** — 비즈니스 규칙 검증, GIVEN-WHEN-THEN 패턴
- **통합 테스트** — 실제 DB 연동 (Mock 금지)
- **E2E 테스트** — API 전 엔드포인트 100% 커버
- **코드·라인 커버리지** 95% 이상 목표
- REST Docs 기반 API 문서 자동화 (빌드 시 OpenAPI 생성 → Redocly 퍼블리시)

테스트 시나리오: [`docs/테스트 시나리오.md`](docs/테스트%20시나리오.md)

---

## 공통 응답 포맷

```json
{
  "success": true,
  "status": 200,
  "data": { }
}
```

```json
{
  "success": false,
  "status": 400,
  "errorCode": "INVALID_INPUT"
}
```

오류 코드: `INVALID_INPUT` · `UNAUTHORIZED` · `FORBIDDEN` · `NOT_FOUND` · `CONFLICT` · `BUSINESS_RULE_VIOLATION` · `CONCURRENT_UPDATE` 등

---

## 문서 목록

| 문서 | 경로 |
|---|---|
| API 명세 (전체) | [`docs/장인몰_API_명세_v1.csv`](docs/장인몰_API_명세_v1.csv) |
| API 공통 규칙 | [`docs/API_공통규칙.md`](docs/API_공통규칙.md) |
| 공개 API 계약 | [`docs/장인몰_API_계약서_공개조회.md`](docs/장인몰_API_계약서_공개조회.md) |
| 인증 정책 계약 | [`docs/PHASE2-2_인증_정책_계약서.md`](docs/PHASE2-2_인증_정책_계약서.md) |
| AI 통합 계약 | [`docs/PHASE2-3_AI_통합_계약서.md`](docs/PHASE2-3_AI_통합_계약서.md) |
| ERD 설계 | [`docs/ERD_설계.md`](docs/ERD_설계.md) |
| 동시성 처리 전략 | [`docs/장인몰_동시성_처리_전략.csv`](docs/장인몰_동시성_처리_전략.csv) |
| 서버 아키텍처 | [`docs/서버_아키텍처.md`](docs/서버_아키텍처.md) |
| 도메인 아키텍처 | [`docs/장인몰_아키텍처_강정훈담당.md`](docs/장인몰_아키텍처_강정훈담당.md) |
| 기술 스택 | [`docs/기술_스택.md`](docs/기술_스택.md) |
| 예외 설계 | [`docs/예외_설계.md`](docs/예외_설계.md) |
| **코드 규칙** | [`docs/코드_규칙.md`](docs/코드_규칙.md) |
| 테스트 시나리오 | [`docs/테스트 시나리오.md`](docs/테스트%20시나리오.md) |
| 샘플 데이터 설계 | [`docs/장인몰_샘플데이터_아키텍처.md`](docs/장인몰_샘플데이터_아키텍처.md) |
