#!/usr/bin/env bash
# DAST 테스트 계정 생성 스크립트
# AWS Parameter Store에서 값을 읽어 회원가입 API를 호출한다.
#
# 사전 조건:
#   - aws cli 설치 및 인증 완료 (aws sts get-caller-identity)
#   - curl 설치
#   - RDS Data API 활성화된 Aurora 클러스터 또는 DB_CLUSTER_ARN/DB_SECRET_ARN 설정
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

DB_CLUSTER_ARN=$(fetch_param "$PARAM_PREFIX/db-cluster-arn")
DB_SECRET_ARN=$(fetch_param "$PARAM_PREFIX/db-secret-arn")
DB_NAME=$(fetch_param "$PARAM_PREFIX/db-name")

signup() {
    local email="$1"
    local password="$2"
    local name="$3"
    local phone="$4"

    http_code=$(curl -s -o /tmp/dast_resp.json -w "%{http_code}" \
        -X POST "$BASE_URL/api/member/signup" \
        -H "Content-Type: application/json" \
        -d "{
            \"email\": \"$email\",
            \"password\": \"$password\",
            \"passwordConfirm\": \"$password\",
            \"name\": \"$name\",
            \"phone\": \"$phone\",
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
    aws rds-data execute-statement \
        --resource-arn "$DB_CLUSTER_ARN" \
        --secret-arn "$DB_SECRET_ARN" \
        --database "$DB_NAME" \
        --sql "UPDATE member SET role = '$role' WHERE email = '$email'" \
        --query "numberOfRecordsUpdated" \
        --output text
}

# USER
result=$(signup "$DAST_USER_EMAIL" "$DAST_USER_PASSWORD" "DAST유저" "01000000001")
[ "$result" = "created" ] && echo "✓ USER ($DAST_USER_EMAIL) 생성 완료" || echo "- USER ($DAST_USER_EMAIL) 이미 존재 — 건너뜀"

# ARTISAN
result=$(signup "$DAST_ARTISAN_EMAIL" "$DAST_ARTISAN_PASSWORD" "DAST장인" "01000000002")
if [ "$result" = "created" ]; then
    set_role "$DAST_ARTISAN_EMAIL" "ARTISAN"
    echo "✓ ARTISAN ($DAST_ARTISAN_EMAIL) 생성 및 role 적용 완료"
else
    echo "- ARTISAN ($DAST_ARTISAN_EMAIL) 이미 존재 — 건너뜀"
fi

# ADMIN — USER로 가입 후 즉시 ADMIN으로 role 변경
result=$(signup "$DAST_ADMIN_EMAIL" "$DAST_ADMIN_PASSWORD" "DAST어드민" "01000000003")
if [ "$result" = "created" ]; then
    set_role "$DAST_ADMIN_EMAIL" "ADMIN"
    echo "✓ ADMIN ($DAST_ADMIN_EMAIL) 생성 및 role 적용 완료"
else
    echo "- ADMIN ($DAST_ADMIN_EMAIL) 이미 존재 — 건너뜀"
fi

echo ""
echo "=== 완료 ==="
