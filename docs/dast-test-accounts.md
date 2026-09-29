# DAST 테스트 계정 설정 가이드

## AWS Parameter Store 경로 구조

| 경로 | 설명 | 타입 |
|------|------|------|
| `/midam/dast/user/email` | USER 역할 테스트 계정 이메일 | String |
| `/midam/dast/user/password` | USER 역할 테스트 계정 비밀번호 | SecureString |
| `/midam/dast/artisan/email` | ARTISAN 역할 테스트 계정 이메일 | String |
| `/midam/dast/artisan/password` | ARTISAN 역할 테스트 계정 비밀번호 | SecureString |
| `/midam/dast/admin/email` | ADMIN 역할 테스트 계정 이메일 | String |
| `/midam/dast/admin/password` | ADMIN 역할 테스트 계정 비밀번호 | SecureString |

## AWS CLI로 파라미터 등록

```bash
# USER
aws ssm put-parameter --name "/midam/dast/user/email"    --value "dast-user@midam.store"    --type String
aws ssm put-parameter --name "/midam/dast/user/password" --value "<비밀번호>"                --type SecureString

# ARTISAN
aws ssm put-parameter --name "/midam/dast/artisan/email"    --value "dast-artisan@midam.store"    --type String
aws ssm put-parameter --name "/midam/dast/artisan/password" --value "<비밀번호>"                   --type SecureString

# ADMIN
aws ssm put-parameter --name "/midam/dast/admin/email"    --value "dast-admin@midam.store"    --type String
aws ssm put-parameter --name "/midam/dast/admin/password" --value "<비밀번호>"                 --type SecureString
```

비밀번호 규칙: 8자 이상, 영문+숫자+특수문자 조합 권장

## 계정 생성 절차

```bash
# 1. prod 서버 대상으로 실행
BASE_URL=https://api.midam.store ./scripts/init-dast-accounts.sh

# 2. ADMIN 계정 role 수동 변경 (RDS 직접 접속 후)
UPDATE member SET role = 'ADMIN' WHERE email = 'dast-admin@midam.store';
```

## 계정 역할 정리

| 계정 | 역할 | 용도 |
|------|------|------|
| `dast-user@midam.store` | USER | 일반 사용자 API 테스트 |
| `dast-artisan@midam.store` | ARTISAN | 장인 전용 API 테스트 (상품 등록 등) |
| `dast-admin@midam.store` | ADMIN | 관리자 API 테스트 |

## 주의사항

- 비밀번호는 SecureString 타입으로 저장해 KMS 암호화 적용
- DAST 완료 후 테스트 계정 비활성화 또는 삭제 권장
- `init-dast-accounts.sh` 실행 전 `aws sts get-caller-identity`로 인증 확인
