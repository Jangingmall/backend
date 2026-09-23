# J. Smoke Test Set

배포 직후 최소 검증 항목. 전체 실패 시 롤백 고려.

| TestID | Method | Endpoint | Expected | Auth | Priority |
|--------|--------|----------|----------|------|----------|
| SMOKE-001 | GET | /healthz | 200 OK | PUBLIC | CRITICAL |
| SMOKE-002 | GET | /actuator/health/liveness | 200 OK | PUBLIC | CRITICAL |
| SMOKE-003 | GET | /actuator/health/readiness | 200 OK | PUBLIC | CRITICAL |
| SMOKE-004 | POST | /api/member/login | 200 + accessToken | PUBLIC | CRITICAL |
| SMOKE-005 | GET | /api/member/me | 200 | USER | CRITICAL |
| SMOKE-006 | GET | /api/products | 200 | PUBLIC | HIGH |
| SMOKE-007 | GET | /api/products/categories | 200 | PUBLIC | HIGH |
| SMOKE-008 | GET | /api/payments/cart | 200 | USER | HIGH |
| SMOKE-009 | POST | /api/member/token/refresh | 200 + newToken | USER | HIGH |
| SMOKE-010 | POST | /api/member/logout | 200 | USER | MEDIUM |

CONFIG_REQUIRED: 테스트 계정 (email, password) — CI 환경변수 등록 필요
BUSINESS_PRIORITY_REQUIRED: CRITICAL/HIGH 분류는 비즈니스 우선순위 팀 확인 필요
