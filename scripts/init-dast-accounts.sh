#!/usr/bin/env bash
# DAST 테스트 계정 생성 스크립트
# AWS Parameter Store에서 값을 읽어 회원가입 API를 호출한다.
#
# 사전 조건:
#   - aws cli 설치 및 인증 완료 (aws sts get-caller-identity)
#   - curl 설치
#
# 사용법:
#   BASE_URL=https://api.midam.store ./scripts/init-dast-accounts.sh

set -euo pipefail

BASE_URL="${BASE_URL:-https://api.midam.store}"
PARAM_PREFIX="/midam/dast"

echo "=== DAST 테스트 계정 생성 ==="
echo "대상 서버: $BASE_URL"

# Parameter Store에서 값 읽기
fetch_param() {
    aws ssm get-parameter --name "$1" --with-decryption --query "Parameter.Value" --output text
}

DAST_USER_EMAIL=$(fetch_param "$PARAM_PREFIX/user/email")
DAST_USER_PASSWORD=$(fetch_param "$PARAM_PREFIX/user/password")

DAST_ARTISAN_EMAIL=$(fetch_param "$PARAM_PREFIX/artisan/email")
DAST_ARTISAN_PASSWORD=$(fetch_param "$PARAM_PREFIX/artisan/password")

DAST_ADMIN_EMAIL=$(fetch_param "$PARAM_PREFIX/admin/email")
DAST_ADMIN_PASSWORD=$(fetch_param "$PARAM_PREFIX/admin/password")

signup() {
    local email="$1"
    local password="$2"
    local name="$3"
    local role="$4"
    local phone="$5"

    http_code=$(curl -s -o /tmp/dast_resp.json -w "%{http_code}" \
        -X POST "$BASE_URL/api/member/signup" \
        -H "Content-Type: application/json" \
        -d "{
            \"email\": \"$email\",
            \"password\": \"$password\",
            \"passwordConfirm\": \"$password\",
            \"name\": \"$name\",
            \"phone\": \"$phone\",
            \"role\": \"$role\",
            \"agreements\": {
                \"age14OrOlder\": true,
                \"termsOfService\": true,
                \"privacyCollection\": true,
                \"marketing\": false
            }
        }")

    if [ "$http_code" = "201" ] || [ "$http_code" = "200" ]; then
        echo "✓ $role ($email) 생성 완료"
    else
        echo "✗ $role ($email) 실패 (HTTP $http_code)"
        cat /tmp/dast_resp.json
    fi
}

signup "$DAST_USER_EMAIL"    "$DAST_USER_PASSWORD"    "DAST유저"   "USER"    "01000000001"
signup "$DAST_ARTISAN_EMAIL" "$DAST_ARTISAN_PASSWORD" "DAST장인"   "ARTISAN" "01000000002"
signup "$DAST_ADMIN_EMAIL"   "$DAST_ADMIN_PASSWORD"   "DAST어드민" "USER"    "01000000003"

echo ""
echo "=== ADMIN 역할 부여 ==="
echo "주의: ADMIN 계정($DAST_ADMIN_EMAIL)은 가입 후 DB에서 role을 'ADMIN'으로 수동 변경해야 합니다."
echo "  UPDATE member SET role = 'ADMIN' WHERE email = '$DAST_ADMIN_EMAIL';"
echo ""
echo "완료. Parameter Store 경로:"
echo "  $PARAM_PREFIX/user/email|password"
echo "  $PARAM_PREFIX/artisan/email|password"
echo "  $PARAM_PREFIX/admin/email|password"
