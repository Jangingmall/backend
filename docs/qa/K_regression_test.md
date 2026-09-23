# K. Regression Test Set

CI/CD 자동 회귀 테스트 포함 항목.

## 분류 기준

| Category | 포함 기준 | 예상 케이스 수 |
|----------|---------|--------------|
| SMOKE | 헬스체크 + 핵심 인증 | 10 |
| CORE_FUNCTIONAL | CORE 도메인 Happy Path 전체 | ~60 |
| AUTH | 인증/만료/무효 토큰 (인증 필요 API 전체) | ~90 |
| AUTHORIZATION | 역할별 접근 (AUTH Matrix 기반) | ~113 |
| NEGATIVE | 필수값 누락, 타입 오류, 경계값 | ~287 |
| BOUNDARY | min/max/minLen/maxLen/enum 경계 | ~200 |
| CONTRACT | Response Schema 검증 | ~160 |
| INTEGRATION | API Chaining Scenario (SCN-001~007) | ~35 |

## CI 실행 전략

```
PR 단계:   SMOKE + CONTRACT (빠른 피드백, ~2분)
develop:   SMOKE + CORE_FUNCTIONAL + AUTH + NEGATIVE (~10분)
release:   전체 (SMOKE + CORE + AUTH + AUTHZ + NEG + BOUNDARY + CONTRACT + INTEGRATION, ~25분)
```

RELEASE_POLICY_REQUIRED: 어느 범주 실패 시 배포 차단할지 팀 결정 필요
