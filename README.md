# 장인몰 (Jangingmall) — 백엔드

[![GitHub](https://img.shields.io/badge/GitHub-Jangingmall%2Fbackend-181717?logo=github)](https://github.com/Jangingmall/backend)

국가무형유산 장인의 공예 작품과 가치를 소비자에게 연결하는 B2C 공예 전문 커머스 플랫폼 **"미담"** 의 백엔드 서버입니다.

---

## 프로젝트 개요

### 서비스 정의

| 항목 | 내용 |
| --- | --- |
| 서비스명 | 장인몰 (가제) / 미담 |
| 유형 | B2C 종합 마켓플레이스 |
| 핵심 가치 | 국가 공인 인증 기반 신뢰성 확보 + 장인의 안정적 판로 제공 |
| MVP 배포 목표 | 2026-09-21 |

### 해결하는 문제

| 대상 | 핵심 문제 |
| --- | --- |
| 공급자 (장인) | 온라인 판로 부재 및 디지털 운영 부담 — 공예품 직접 판매 의존 71.7%, 장인 평균 연령 75세 |
| 수요자 (소비자) | 국가 공인 장인 여부 및 작품 가치 판단의 어려움 — 기존 플랫폼에 자격 식별 수단 미흡 |

---

## MVP 솔루션 범위

| 우선순위 | 솔루션 | MVP 포함 |
| --- | --- | --- |
| **P0** | AI 상세페이지 자동 생성 + 장인 승인 구조 | ✅ |
| **P0** | 취재 데이터 구조화 (제작과정·기법·소재) | ✅ (화면 구현 제외) |
| **P1** | 장인 브랜드관 + 공인 인증 배지 + 경력·전승 정보 | ✅ |
| **P1** | 취향·용도 큐레이션 · 통합 검색/필터 | ✅ |
| **P2** | 주문제작·한정수량 / 후기 / 장바구니 | 🟡 표기·표시 수준으로 축소 |

---

## 시스템 아키텍처

### 팀별 역할 분담

| 파트 | 담당 |
| --- | --- |
| 백엔드 | 커머스 원본 DB(상품·장인·주문·결제), 장인 ID 발급, 세션·대화이력 관리, AI 호출, 상품 상세 조립 |
| 프론트 | 입력 UI, 백엔드 챗봇 API 호출, ISR 캐시 관리, 추천 결과 렌더링 |
| AI 파트 | 추천 챗봇 + 자체 벡터 DB(pgvector) 운영 |
| 인프라 | VPC/EKS, PostgreSQL(EC2), S3, CI/CD(GitHub Actions + ArgoCD), 모니터링 |
| 보안 | 위협 모델링, SAST/DAST, 계정·결제 무결성 검토 |

### AI 추천 챗봇 흐름

```
[소비자]
   │ 자연어 입력 ("엄마 환갑 선물 5만원 이하, 고급스러운 걸로")
   ▼
[프론트] ─── 챗봇 메시지 전송 ───▶ [백엔드]
   ▲                                 │ ① 메시지·세션 저장
   │                                 │ ② POST /ai/chat 호출
   │                                 ▼
   │                              [AI 서버]
   │                                 │ ① 의도분류·조건추출 (Qwen3)
   │                                 │ ② 쿼리 임베딩 (KURE-v1)
   │                                 │ ③ 하이브리드 검색 (Vector + BM25 + RRF)
   │                                 │ ④ 인증 등급 가중 랭킹
   │                                 │ ⑤ 근거 기반 추천이유 생성 (Qwen3)
   │                                 ▼
   │ ◀── 카드 조립 + reply ───── [백엔드]
   ▼
[모달 렌더링]
```

### AI 기술 스택

| 역할 | 선택 | 비고 |
| --- | --- | --- |
| DB | PostgreSQL + pgvector | AI 자체 운영, 로컬 |
| 임베딩 | KURE-v1 (1024차원) | 한국어 특화 |
| LLM | Qwen3 (Ollama) | MacBook M4 24GB 로컬 |
| BM25 | rank_bm25 + 형태소(mecab/Kiwi) | 애플리케이션단 |
| 서버 | FastAPI | `/ai/*` 엔드포인트 |

### 인프라 구성 (MVP 기준)

| 항목 | 기술 |
| --- | --- |
| 컨테이너 오케스트레이션 | AWS EKS |
| DB | PostgreSQL (EC2) |
| 스토리지 | AWS S3 (이미지, 서류) |
| CI/CD | GitHub Actions + ArgoCD |
| 모니터링 | Prometheus / Grafana / CloudWatch |
| 시크릿 관리 | AWS SSM / Secrets Manager |
| 결제 | 토스페이먼츠 |

---

## 백엔드 API 도메인 구조

총 **95개 엔드포인트**, 8개 도메인으로 구성됩니다.  
전체 명세: [`docs/장인몰_API_명세_v1.csv`](docs/장인몰_API_명세_v1.csv)

| 도메인 | 기본 경로 | API 수 | 담당 |
| --- | --- | --- | --- |
| 회원 | `/api/member/**` | 36 | 유창민 |
| 결제 | `/api/payments/**` | 17 | 유창민 |
| 관리자 | `/api/admin/**` | 4 | 유창민 |
| 이미지 | `/api/images/**` | 3 | 유창민 |
| 상품 | `/api/products/**` | 16 | 강정훈 |
| 컨텐츠 | `/api/content/**` | 11 | 강정훈 |
| 알림 | `/api/notifications/**` | 4 | 강정훈 |
| 챗봇 | `/api/chatbot/**` | 4 | 강정훈 |

### 주요 엔드포인트 요약

#### 회원 (`/api/member`)
| 메소드 | 경로 | 기능 |
| --- | --- | --- |
| `POST` | `/api/member/signup` | 이메일 회원가입 |
| `POST` | `/api/member/login` | 로그인 |
| `GET` | `/api/member/oauth2/kakao` | 카카오 OAuth2 로그인 |
| `GET` | `/api/member/oauth2/google` | 구글 OAuth2 로그인 |
| `GET` | `/api/member/me` | 내 정보 조회 |
| `GET` | `/api/member/artisans/{artisanId}` | 장인 프로필 조회 |
| `GET` | `/api/member/artisans` | 장인 목록 조회 (필터/정렬) |

#### 상품 (`/api/products`)
| 메소드 | 경로 | 기능 |
| --- | --- | --- |
| `GET` | `/api/products` | 상품 목록 검색/필터 (6종 정렬, 다중 필터) |
| `GET` | `/api/products/{productId}` | 상품 상세 조회 |
| `POST` | `/api/products` | 상품 등록 (ARTISAN) |
| `PATCH` | `/api/products/{productId}` | 상품 수정 |
| `POST` | `/api/products/{productId}/wish` | 찜 등록 |

#### 결제 (`/api/payments`)
| 메소드 | 경로 | 기능 |
| --- | --- | --- |
| `GET` | `/api/payments/cart` | 장바구니 조회 (게스트 허용) |
| `POST` | `/api/payments/cart/items` | 장바구니 상품 추가 |
| `POST` | `/api/payments/orders` | 주문 생성 |
| `POST` | `/api/payments` | 결제 준비 (토스페이먼츠) |
| `POST` | `/api/payments/confirm` | 결제 승인 |
| `POST` | `/api/payments/{paymentId}/cancel` | 결제 취소/환불 |

#### 컨텐츠 (`/api/content`)
| 메소드 | 경로 | 기능 |
| --- | --- | --- |
| `POST` | `/api/content/products/{productId}/interview` | 취재 데이터 등록 |
| `POST` | `/api/content/products/{productId}/generations` | AI 상세페이지 생성 요청 |
| `GET` | `/api/content/products/{productId}/contents` | AI 생성 결과 조회 |
| `POST` | `/api/content/products/{productId}/contents/{contentId}/approve` | 콘텐츠 장인 승인 |
| `POST` | `/api/content/products/{productId}/publish` | 상품 게시 (AI파트 동기화 포함) |

#### 챗봇 (`/api/chatbot`)
| 메소드 | 경로 | 기능 |
| --- | --- | --- |
| `POST` | `/api/chatbot/sessions` | 챗봇 세션 생성 |
| `POST` | `/api/chatbot/sessions/{sessionId}/messages` | 메시지 전송 (AI 추천 응답) |
| `GET` | `/api/chatbot/sessions/{sessionId}/messages` | 대화 히스토리 조회 |

---

## 인증 체계

| 구분 | 설명 |
| --- | --- |
| `Public` | 인증 불필요 |
| `Public (게스트 허용)` | 비회원은 쿠키의 `guestCartId`로 식별 |
| `Authenticated` | JWT Bearer 토큰 필요 |
| `USER` | 소비자 역할 |
| `ARTISAN` | 장인(판매자) 역할 |
| `ADMIN` | 관리자 역할 |

공통 응답 포맷:
```json
{
  "success": true,
  "status": 200,
  "data": { ... }
}
```

---

## FE-BE 캐시 재검증 계약

백엔드는 상품·장인 정보 변경 커밋 후 FE ISR 캐시 재검증 이벤트를 전달합니다.

| 이벤트 | 발생 시점 |
| --- | --- |
| `product.contentPublished` | `POST /api/content/products/{productId}/publish` 성공 |
| `artisan.profileUpdated` | 장인 프로필 변경 커밋 |

- Method: `POST`, HMAC-SHA256 서명 포함
- 전송 실패 시 재시도 및 최종 실패 추적 필수

---

## RACI 매트릭스 요약

| 태스크 | BE 역할 |
| --- | --- |
| API 명세 & DB 스키마 설계 | **R/A** (최종 책임) |
| 백엔드 API 서버 개발 | **R/A** (최종 책임) |
| AI 엔진 구현 & BE 통합 | **R** (실무 담당) |
| 기능 통합 테스트 & QA | **R** (실무 담당) |
| 보안 검토 | **C** (협의 대상) |

---

## 관련 문서

| 문서 | 경로 |
| --- | --- |
| API 명세 (CSV) | [`docs/장인몰_API_명세_v1.csv`](docs/장인몰_API_명세_v1.csv) |
| 백엔드 코드 규칙 | [`.claude/rules/java-spring-rules.md`](.claude/rules/java-spring-rules.md) |
| 코드 스타일 | [`.claude/rules/java-code-style.md`](.claude/rules/java-code-style.md) |
