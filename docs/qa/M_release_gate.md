# M. Release Gate Candidates

RELEASE_POLICY_REQUIRED: 아래는 후보 목록. 실제 차단 기준은 팀 결정 필요.

| ID | 조건 | 제안 수준 | 결정 필요 |
|----|------|---------|---------|
| GATE-001 | SMOKE-001~003 (헬스체크) 실패 | BLOCK | YES |
| GATE-002 | SMOKE-004 (로그인) 실패 | BLOCK | YES |
| GATE-003 | CONTRACT Breaking Change 감지 | BLOCK | YES |
| GATE-004 | CORE 도메인 Happy Path 실패 | BLOCK | YES |
| GATE-005 | 인증 (401) 응답 누락 | BLOCK | YES |
| GATE-006 | 결제 흐름 (SCN-004) 실패 | BLOCK | YES |
| GATE-007 | NEGATIVE 테스트 실패율 > 10% | WARN | YES |
| GATE-008 | Response Time > 3s (p95) | WARN | YES |
| GATE-009 | 권한 우회 (IDOR) 탐지 | BLOCK | YES |

## CONTRACT Breaking Change 탐지 기준

```
아래 중 하나라도 발생 시 CONTRACT_BREAKING_CHANGE:
- 기존 Response 필드 제거
- 필드 타입 변경 (integer → string 등)
- 필수 Request 필드 추가
- 기존 Status Code 변경
- Enum 값 제거
```
