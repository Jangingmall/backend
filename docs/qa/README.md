# QA Automation Package — Executive Summary

생성일: 2026-09-23
Source: api-spec/openapi.json
도구: openapi.json 자동 파싱 + 수동 검토

---

## 1. Executive Summary

장인몰(미담) REST API 113개 엔드포인트를 대상으로 QA 자동화 전체 설계를 수행했다.
OpenAPI 계약을 1차 Source of Truth로 사용하였으며, 스펙에 없는 비즈니스 규칙은 SPEC_GAP으로 표시했다.

**즉시 자동화 가능 (AUTO)**: 781건 테스트 케이스 (해피패스 + 필수값 + 타입 + 경계값 + 인증)
**환경 설정 후 가능 (CONFIG_REQUIRED)**: 테스트 계정 / 스테이징 URL / Toss sandbox
**백엔드 정보 필요 (BACKEND_INFO_REQUIRED)**: 주문 상태 enum, DB schema, AI mock
**팀 결정 필요 (PRODUCT_REQUIREMENT_REQUIRED)**: Release Gate 기준, 반품 정책 등

---

## 2. OpenAPI Statistics

| 항목 | 값 |
|------|-----|
| 전체 엔드포인트 | 113 |
| 도메인(태그) 수 | 28 |
| 인증 필요 API | 96 |
| 공개(인증 불필요) API | 17 |
| 컴포넌트 스키마 수 | 109 |

### 역할별 분포

| Role | 개수 |
|------|------|
| PUBLIC (인증 불필요) | 17 |
| USER | 72 |
| ARTISAN | 19 |
| ADMIN | 5 |

### 도메인별 API 수

| 도메인 | API 수 |
|--------|--------|
| 콘텐츠 | 11 |
| 알림 | 8 |
| 상품 | 7 |
| 회원 | 7 |
| 장바구니 | 7 |
| 주문 | 7 |
| 결제 | 5 |
| 판매자 신청 (관리자) | 5 |
| 챗봇 | 4 |
| 장인 | 4 |
| 결제수단 | 4 |
| 카테고리 | 4 |
| 장인 구독 | 4 |
| 배송지 | 4 |
| 찜 | 4 |
| 인프라 | 4 |
| 이미지 | 3 |
| 회원 계정 | 3 |
| 소셜 로그인 | 3 |
| 상품 문의 | 3 |
| 판매자 신청 | 2 |
| 리뷰 | 2 |
| 상품 후기 | 2 |
| AI 콘텐츠 생성 | 2 |
| 반품/교환 | 1 |
| 배송 | 1 |
| AI 콜백 (내부) | 1 |
| Dev (로컬 전용) | 1 |

---

## 3. 산출물 목록

| 파일 | 내용 | 형식 | 건수 |
|------|------|------|------|
| A_api_inventory.csv | 전체 API 목록 | CSV | 113 |
| B_test_case_matrix.csv | 전체 테스트 케이스 | CSV | 781 |
| C_negative_test_matrix.csv | Negative / 경계값 테스트 | CSV | 287 |
| D_auth_matrix.csv | 역할별 인증/권한 매트릭스 | CSV | 113 |
| E_api_scenarios.md | API 체이닝 시나리오 | Markdown | 7 |
| F_test_data_strategy.md | 테스트 데이터 Setup/Cleanup 전략 | Markdown | - |
| G_db_verification.md | DB 검증 요구사항 | Markdown | - |
| H_external_dependency.md | 외부 의존성 / Mock 요구사항 | Markdown | - |
| I_contract_test_matrix.csv | Response Schema 계약 검증 | CSV | 165 |
| J_smoke_test.md | Smoke Test 항목 | Markdown | 10 |
| K_regression_test.md | 회귀 테스트 전략 | Markdown | - |
| L_cicd_strategy.yml | CI/CD 실행 구조 (GitHub Actions) | YAML | - |
| M_release_gate.md | Release Gate 후보 | Markdown | 9 |
| N_spec_gap.md | SPEC GAP 목록 (자동 감지 84건 포함) | Markdown | 84+ |
| O_backend_qa_request.md | Backend 팀 요청 항목 | Markdown | 14 |

---

## 4. 자동화 분류 요약

| 분류 | 설명 | 해당 항목 |
|------|------|---------|
| AUTO | OpenAPI만으로 즉시 자동화 가능 | B/C/D/I CSV 전체 (1,346건) |
| CONFIG_REQUIRED | 환경값/테스트 계정 필요 | Smoke, 인증 시나리오 |
| BACKEND_INFO_REQUIRED | 주문상태 enum, DB schema, AI mock | G, H, N 일부 |
| PRODUCT_REQUIREMENT_REQUIRED | Release Gate, 반품정책, 비즈니스 우선순위 | M, K, 일부 N |

---

## 5. 권고 실행 순서

```
Week 1:
  ① O_backend_qa_request.md 우선순위 필수 항목 Backend 팀에 요청
  ② 스테이징 환경 + 테스트 계정 확보
  ③ Postman smoke.collection.json 작성 (SMOKE-001~010)
  ④ CI SECRET 등록 (QA_BASE_URL, QA_ACCESS_TOKEN 등)

Week 2:
  ⑤ B_test_case_matrix.csv → Postman core.collection.json 변환
  ⑥ L_cicd_strategy.yml PR/develop 파이프라인 적용
  ⑦ Contract 모니터링 활성화

Week 3+:
  ⑧ Toss sandbox 결제 시나리오 (SCN-004)
  ⑨ ARTISAN/ADMIN 역할 시나리오
  ⑩ 전체 Regression suite 구성
```
