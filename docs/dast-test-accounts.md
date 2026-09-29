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
| `/staging/backend/db-url` | DB 접속 URL (기존) | SecureString |
| `/staging/backend/db-username` | DB 사용자명 (기존) | SecureString |
| `/staging/backend/db-password` | DB 비밀번호 (기존) | SecureString |

## AWS CLI로 파라미터 등록 (최초 1회)

`--overwrite` 없이 등록하면 이미 존재하는 파라미터는 오류 발생 — 중복 저장 방지.

```bash
# USER
aws ssm put-parameter --name "/staging/backend/dast-user-email"    --value "dast-user@midam.store"    --type String
aws ssm put-parameter --name "/staging/backend/dast-user-password" --value "<비밀번호>"                --type SecureString

# ARTISAN
aws ssm put-parameter --name "/staging/backend/dast-artisan-email"    --value "dast-artisan@midam.store"    --type String
aws ssm put-parameter --name "/staging/backend/dast-artisan-password" --value "<비밀번호>"                   --type SecureString

# ADMIN
aws ssm put-parameter --name "/staging/backend/dast-admin-email"    --value "dast-admin@midam.store"    --type String
aws ssm put-parameter --name "/staging/backend/dast-admin-password" --value "<비밀번호>"                 --type SecureString
```

비밀번호 규칙: 8자 이상, 영문+숫자+특수문자 조합

## 계정 생성 절차

```bash
# db-url/db-username/db-password는 기존 Parameter Store 값을 그대로 사용
BASE_URL=https://api.midam.store ./scripts/init-dast-accounts.sh
```

스크립트가 자동으로 처리:
1. USER/ARTISAN/ADMIN 모두 `role: USER`로 회원가입 API 호출
2. ARTISAN → psql로 role `ARTISAN` UPDATE
3. ADMIN → psql로 role `ADMIN` UPDATE
4. 이미 존재하는 계정은 건너뜀

## 계정 역할 정리

| 계정 | 역할 | 용도 |
|------|------|------|
| `dast-user@midam.store` | USER | 일반 사용자 API 테스트 |
| `dast-artisan@midam.store` | ARTISAN | 장인 전용 API 테스트 |
| `dast-admin@midam.store` | ADMIN | 관리자 API 테스트 |

## 적재 검증

```bash
DAST_BASE_URL=https://api.midam.store \
DAST_USER_EMAIL=dast-user@midam.store     DAST_USER_PASSWORD=<pw> \
DAST_ARTISAN_EMAIL=dast-artisan@midam.store DAST_ARTISAN_PASSWORD=<pw> \
DAST_ADMIN_EMAIL=dast-admin@midam.store   DAST_ADMIN_PASSWORD=<pw> \
./gradlew dastInfraTest
```

## 주의사항

- 비밀번호는 SecureString 타입으로 저장 (KMS 암호화)
- `--overwrite` 없이 등록해 1회만 저장 가능
- 회원가입 API가 USER 외 role 직접 지정을 막으므로, ARTISAN/ADMIN role은 psql로 즉시 변경
- DAST 완료 후 테스트 계정 비활성화 또는 삭제 권장
