# DAST 테스트 계정 설정 가이드

## AWS Parameter Store 경로 구조

패턴: `/staging/backend/{main}-{sub}`

| 경로 | 설명 | 타입 |
|------|------|------|
| `/staging/backend/dast-user-email` | USER 테스트 계정 이메일 | String |
| `/staging/backend/dast-user-password` | USER 테스트 계정 비밀번호 | SecureString |
| `/staging/backend/dast-artisan-email` | ARTISAN 테스트 계정 이메일 | String |
| `/staging/backend/dast-artisan-password` | ARTISAN 테스트 계정 비밀번호 | SecureString |
| `/staging/backend/dast-admin-email` | ADMIN 테스트 계정 이메일 | String |
| `/staging/backend/dast-admin-password` | ADMIN 테스트 계정 비밀번호 | SecureString |

## AWS CLI로 파라미터 등록 (최초 1회)

`--overwrite` 없이 등록하면 이미 존재하는 파라미터는 오류 발생 — 중복 저장 방지.

```bash
# USER
aws ssm put-parameter \
  --name "/staging/backend/dast-user-email" \
  --value "dast-user@midam.store" \
  --type String

aws ssm put-parameter \
  --name "/staging/backend/dast-user-password" \
  --value "<비밀번호>" \
  --type SecureString

# ARTISAN
aws ssm put-parameter \
  --name "/staging/backend/dast-artisan-email" \
  --value "dast-artisan@midam.store" \
  --type String

aws ssm put-parameter \
  --name "/staging/backend/dast-artisan-password" \
  --value "<비밀번호>" \
  --type SecureString

# ADMIN
aws ssm put-parameter \
  --name "/staging/backend/dast-admin-email" \
  --value "dast-admin@midam.store" \
  --type String

aws ssm put-parameter \
  --name "/staging/backend/dast-admin-password" \
  --value "<비밀번호>" \
  --type SecureString
```

비밀번호 규칙: 8자 이상, 영문+숫자+특수문자 조합

## 계정 생성 절차

```bash
# 1. prod 서버 대상으로 실행 (이미 존재하는 계정은 건너뜀)
BASE_URL=https://api.midam.store ./scripts/init-dast-accounts.sh

# 2. ADMIN 계정 role 수동 변경 (RDS 접속 후)
UPDATE member SET role = 'ADMIN' WHERE email = 'dast-admin@midam.store';
```

## 계정 역할 정리

| 계정 | 역할 | 용도 |
|------|------|------|
| `dast-user@midam.store` | USER | 일반 사용자 API 테스트 |
| `dast-artisan@midam.store` | ARTISAN | 장인 전용 API 테스트 |
| `dast-admin@midam.store` | ADMIN | 관리자 API 테스트 |

## 주의사항

- 비밀번호는 SecureString 타입으로 저장 (KMS 암호화)
- `put-parameter`에 `--overwrite` 플래그를 사용하지 않아 1회만 저장 가능
- DAST 완료 후 테스트 계정 비활성화 또는 삭제 권장
