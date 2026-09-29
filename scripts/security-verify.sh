#!/usr/bin/env bash
# 사이버보안팀 전용 DAST 계정 검증 스크립트
# AWS Parameter Store에서 값을 읽어 securityVerify Gradle 태스크를 실행한다.
#
# 사전 조건:
#   - aws cli 설치 및 인증 완료 (aws sts get-caller-identity)
#
# 사용법:
#   ./scripts/security-verify.sh

set -euo pipefail

PARAM_PREFIX="/staging/backend"

fetch_param() {
    aws ssm get-parameter --name "$1" --with-decryption --query "Parameter.Value" --output text
}

echo "=== Parameter Store에서 값 로드 중... ==="

export DAST_BASE_URL="${DAST_BASE_URL:-https://api.midam.store}"
export DAST_USER_EMAIL=$(fetch_param "$PARAM_PREFIX/dast-user-email")
export DAST_USER_PASSWORD=$(fetch_param "$PARAM_PREFIX/dast-user-password")
export DAST_ARTISAN_EMAIL=$(fetch_param "$PARAM_PREFIX/dast-artisan-email")
export DAST_ARTISAN_PASSWORD=$(fetch_param "$PARAM_PREFIX/dast-artisan-password")
export DAST_ADMIN_EMAIL=$(fetch_param "$PARAM_PREFIX/dast-admin-email")
export DAST_ADMIN_PASSWORD=$(fetch_param "$PARAM_PREFIX/dast-admin-password")

echo "대상 서버: $DAST_BASE_URL"
echo ""

./gradlew securityVerify
