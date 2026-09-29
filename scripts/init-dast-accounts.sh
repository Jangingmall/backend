#!/usr/bin/env bash
# DAST 테스트 계정 생성 스크립트
# AWS Parameter Store에서 값을 읽어 회원가입 API를 호출하고
# ARTISAN/ADMIN role을 psql로 즉시 적용한다.
#
# 사전 조건:
#   - aws cli 설치 및 인증 완료 (aws sts get-caller-identity)
#   - curl, psql 설치
#
# 사용법:
#   BASE_URL=https://api.midam.store ./scripts/init-dast-accounts.sh

set -euo pipefail

BASE_URL="${BASE_URL:-https://api.midam.store}"
PARAM_PREFIX="/staging/backend"

echo "=== DAST 테스트 계정 생성 ==="
echo "대상 서버: $BASE_URL"

fetch_param() {
    aws ssm get-parameter --name "$1" --with-decryption --query "Parameter.Value" --output text
}

DAST_USER_EMAIL=$(fetch_param "$PARAM_PREFIX/dast-user-email")
DAST_USER_PASSWORD=$(fetch_param "$PARAM_PREFIX/dast-user-password")

DAST_ARTISAN_EMAIL=$(fetch_param "$PARAM_PREFIX/dast-artisan-email")
DAST_ARTISAN_PASSWORD=$(fetch_param "$PARAM_PREFIX/dast-artisan-password")

DAST_ADMIN_EMAIL=$(fetch_param "$PARAM_PREFIX/dast-admin-email")
DAST_ADMIN_PASSWORD=$(fetch_param "$PARAM_PREFIX/dast-admin-password")

DB_URL=$(fetch_param "$PARAM_PREFIX/db-url")
DB_USERNAME=$(fetch_param "$PARAM_PREFIX/db-username")
DB_PASSWORD=$(fetch_param "$PARAM_PREFIX/db-password")

signup() {
    local email="$1"
    local password="$2"
    local role="$3"

    http_code=$(curl -s -o /tmp/dast_resp.json -w "%{http_code}" \
        -X POST "$BASE_URL/api/member/signup" \
        -H "Content-Type: application/json" \
        -d "{
            \"email\": \"$email\",
            \"password\": \"$password\",
            \"passwordConfirm\": \"$password\",
            \"name\": \"DAST-$role\",
            \"phone\": \"01000000000\",
            \"role\": \"USER\",
            \"agreements\": {
                \"age14OrOlder\": true,
                \"termsOfService\": true,
                \"privacyCollection\": true,
                \"marketing\": false
            }
        }")

    if [ "$http_code" = "201" ] || [ "$http_code" = "200" ]; then
        echo "created"
    elif [ "$http_code" = "409" ]; then
        echo "exists"
    else
        echo "✗ ($email) 실패 (HTTP $http_code)"
        cat /tmp/dast_resp.json
        exit 1
    fi
}

set_role() {
    local email="$1"
    local role="$2"
    PGPASSWORD="$DB_PASSWORD" psql "$DB_URL" -U "$DB_USERNAME" \
        -c "UPDATE member SET role = '$role' WHERE email = '$email';" -q
}

# USER
result=$(signup "$DAST_USER_EMAIL" "$DAST_USER_PASSWORD" "USER")
[ "$result" = "created" ] && echo "✓ USER ($DAST_USER_EMAIL) 생성 완료" || echo "- USER ($DAST_USER_EMAIL) 이미 존재 — 건너뜀"

# ARTISAN
result=$(signup "$DAST_ARTISAN_EMAIL" "$DAST_ARTISAN_PASSWORD" "ARTISAN")
if [ "$result" = "created" ]; then
    set_role "$DAST_ARTISAN_EMAIL" "ARTISAN"
    echo "✓ ARTISAN ($DAST_ARTISAN_EMAIL) 생성 및 role 적용 완료"
else
    echo "- ARTISAN ($DAST_ARTISAN_EMAIL) 이미 존재 — 건너뜀"
fi

# ADMIN — USER로 가입 후 즉시 ADMIN으로 role 변경
result=$(signup "$DAST_ADMIN_EMAIL" "$DAST_ADMIN_PASSWORD" "ADMIN")
if [ "$result" = "created" ]; then
    set_role "$DAST_ADMIN_EMAIL" "ADMIN"
    echo "✓ ADMIN ($DAST_ADMIN_EMAIL) 생성 및 role 적용 완료"
else
    echo "- ADMIN ($DAST_ADMIN_EMAIL) 이미 존재 — 건너뜀"
fi

echo ""
echo "=== 완료 ==="
