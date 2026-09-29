#!/usr/bin/env bash
# DAST 테스트 계정 생성 스크립트 — 인증된 경로(이메일 인증 + API 승인)
#
# 흐름:
#   stgUser    : 이메일 인증코드 발송 → Redis에서 코드 직접 조회 → verify → signup (USER)
#   stgArtisan : 동일 이메일 인증 → signup (USER) → 셀러 신청 → Admin 토큰으로 승인 (ARTISAN)
#   stgAdmin   : Flyway V11 Migration으로 생성 (부트스트랩 — prod 배포 시 자동 적용)
#
# 사전 조건:
#   - aws cli 인증 완료  (aws sts get-caller-identity)
#   - curl, redis-cli 설치
#   - prod 환경에서만 실행 (SPRING_PROFILES_ACTIVE=prod or BASE_URL이 stg/prod 도메인)
#
# 사용법:
#   BASE_URL=https://stg.midam.store ./scripts/init-dast-accounts.sh

set -euo pipefail

BASE_URL="${BASE_URL:-https://stg.midam.store}"
PARAM_PREFIX="/staging/backend"

# ── prod 환경 가드 ────────────────────────────────────────────────────────────
if [[ "$BASE_URL" == *"localhost"* || "$BASE_URL" == *"127.0.0.1"* ]]; then
    echo "✗ 로컬 환경에서는 실행할 수 없습니다. BASE_URL을 스테이징/프로덕션으로 설정하세요."
    exit 1
fi

echo "=== DAST 테스트 계정 생성 ==="
echo "대상 서버: $BASE_URL"
echo ""

# ── SSM 파라미터 읽기 ──────────────────────────────────────────────────────────
ssm() { aws ssm get-parameter --name "$1" --with-decryption --query "Parameter.Value" --output text; }

echo "[1/6] SSM 파라미터 읽기..."
USER_EMAIL=$(ssm "$PARAM_PREFIX/dast-user-email")
USER_PW=$(ssm "$PARAM_PREFIX/dast-user-password")
ARTISAN_EMAIL=$(ssm "$PARAM_PREFIX/dast-artisan-email")
ARTISAN_PW=$(ssm "$PARAM_PREFIX/dast-artisan-password")
ADMIN_EMAIL=$(ssm "$PARAM_PREFIX/dast-admin-email")
ADMIN_PW=$(ssm "$PARAM_PREFIX/dast-admin-password")
REDIS_HOST=$(ssm "$PARAM_PREFIX/redis-host")
REDIS_PORT=$(ssm "$PARAM_PREFIX/redis-port" 2>/dev/null || echo "6379")
DISCORD_WEBHOOK=$(ssm "$PARAM_PREFIX/discord-notification-webhook" 2>/dev/null || echo "")
echo "    완료"

# ── 헬퍼 함수 ─────────────────────────────────────────────────────────────────

# 이메일 SHA-256 해시 → Redis 키
email_redis_key() {
    local email
    email=$(echo -n "$1" | tr '[:upper:]' '[:lower:]')
    local hash
    hash=$(echo -n "$email" | openssl dgst -sha256 -hex | awk '{print $2}')
    echo "member:email-verify:$hash"
}

# 이메일 인증코드 발송 + Redis에서 코드 직접 조회 후 verify
email_verify() {
    local email="$1"
    echo "    이메일 인증코드 발송: $email"
    curl -sf -o /dev/null -X POST "$BASE_URL/api/member/email/verification-code" \
        -H "Content-Type: application/json" \
        -d "{\"email\":\"$email\"}"

    local key
    key=$(email_redis_key "$email")
    local code
    code=$(redis-cli -h "$REDIS_HOST" -p "$REDIS_PORT" GET "$key")
    if [ -z "$code" ]; then
        echo "✗ Redis에서 인증코드를 찾을 수 없습니다 (key=$key)"
        exit 1
    fi
    echo "    인증코드 확인 완료 (code=${code:0:1}***)"

    curl -sf -o /dev/null -X POST "$BASE_URL/api/member/email/verify" \
        -H "Content-Type: application/json" \
        -d "{\"email\":\"$email\",\"code\":\"$code\"}"
    echo "    이메일 인증 완료"
}

# 회원가입
signup() {
    local email="$1" pw="$2" label="$3"
    local http_code
    http_code=$(curl -s -o /tmp/dast_resp.json -w "%{http_code}" \
        -X POST "$BASE_URL/api/member/signup" \
        -H "Content-Type: application/json" \
        -d "{
            \"email\":\"$email\",
            \"password\":\"$pw\",
            \"passwordConfirm\":\"$pw\",
            \"name\":\"DAST-$label\",
            \"phone\":\"01000000000\",
            \"role\":\"USER\",
            \"agreements\":{\"age14OrOlder\":true,\"termsOfService\":true,\"privacyCollection\":true,\"marketing\":false}
        }")
    if [ "$http_code" = "201" ]; then
        echo "created"
    elif [ "$http_code" = "409" ]; then
        echo "exists"
    else
        echo "✗ 회원가입 실패 ($email) HTTP $http_code"
        cat /tmp/dast_resp.json
        exit 1
    fi
}

# 로그인 → accessToken 반환
login() {
    local email="$1" pw="$2"
    local token
    token=$(curl -sf -X POST "$BASE_URL/api/member/login" \
        -H "Content-Type: application/json" \
        -d "{\"email\":\"$email\",\"password\":\"$pw\"}" \
        | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['accessToken'])")
    echo "$token"
}

# Discord 알림 전송
notify_discord() {
    [ -z "$DISCORD_WEBHOOK" ] && return
    local color="$1" msg="$2"
    curl -sf -o /dev/null -X POST "$DISCORD_WEBHOOK" \
        -H "Content-Type: application/json" \
        -d "{\"username\":\"미담 계정 초기화\",\"embeds\":[{\"description\":\"$msg\",\"color\":$color}]}"
}

# ── stgUser ───────────────────────────────────────────────────────────────────
echo "[2/6] stgUser 처리..."
result=$(signup "$USER_EMAIL" "$USER_PW" "USER")
if [ "$result" = "created" ]; then
    email_verify "$USER_EMAIL"
    echo "✓ stgUser ($USER_EMAIL) 생성 완료"
else
    echo "- stgUser ($USER_EMAIL) 이미 존재 — 건너뜀"
fi

# ── stgArtisan ────────────────────────────────────────────────────────────────
echo "[3/6] stgArtisan 처리..."
result=$(signup "$ARTISAN_EMAIL" "$ARTISAN_PW" "ARTISAN")
if [ "$result" = "created" ]; then
    email_verify "$ARTISAN_EMAIL"
    echo "    셀러 신청 중..."
    ARTISAN_TOKEN=$(login "$ARTISAN_EMAIL" "$ARTISAN_PW")
    APPLICATION_ID=$(curl -sf \
        -X POST "$BASE_URL/api/member/artisans/applications" \
        -H "Content-Type: application/json" \
        -H "Authorization: Bearer $ARTISAN_TOKEN" \
        -d '{
            "businessName":"DAST 테스트 공방",
            "introduction":"DAST 자동화 테스트용 계정입니다.",
            "businessLicenseImageUrl":"https://stg.midam.store/static/dast-placeholder.jpg"
        }' | python3 -c "import sys,json; print(json.load(sys.stdin)['applicationId'])")
    echo "    셀러 신청 완료 (applicationId=$APPLICATION_ID)"

    echo "    Admin 토큰으로 승인 중..."
    ADMIN_TOKEN=$(login "$ADMIN_EMAIL" "$ADMIN_PW")
    curl -sf -o /dev/null \
        -X POST "$BASE_URL/api/admin/seller-applications/$APPLICATION_ID/approve" \
        -H "Authorization: Bearer $ADMIN_TOKEN"
    echo "✓ stgArtisan ($ARTISAN_EMAIL) 생성 및 ARTISAN 승인 완료"
else
    echo "- stgArtisan ($ARTISAN_EMAIL) 이미 존재 — 건너뜀"
fi

# ── stgAdmin ──────────────────────────────────────────────────────────────────
echo "[4/6] stgAdmin 처리..."
echo "    stgAdmin은 Flyway V11 Migration으로 생성됩니다 (prod 배포 시 자동 적용)"
ADMIN_LOGIN_CODE=$(curl -s -o /tmp/admin_login.json -w "%{http_code}" \
    -X POST "$BASE_URL/api/member/login" \
    -H "Content-Type: application/json" \
    -d "{\"email\":\"$ADMIN_EMAIL\",\"password\":\"$ADMIN_PW\"}")
if [ "$ADMIN_LOGIN_CODE" = "200" ]; then
    echo "✓ stgAdmin ($ADMIN_EMAIL) 로그인 확인 완료"
else
    echo "⚠ stgAdmin ($ADMIN_EMAIL) 로그인 실패 — V11 Migration이 아직 미적용일 수 있습니다"
fi

# ── 검증 ──────────────────────────────────────────────────────────────────────
echo "[5/6] 계정 로그인 최종 검증..."
for pair in "$USER_EMAIL:$USER_PW:USER" "$ARTISAN_EMAIL:$ARTISAN_PW:ARTISAN" "$ADMIN_EMAIL:$ADMIN_PW:ADMIN"; do
    email="${pair%%:*}"
    rest="${pair#*:}"
    pw="${rest%%:*}"
    label="${rest##*:}"
    code=$(curl -s -o /dev/null -w "%{http_code}" \
        -X POST "$BASE_URL/api/member/login" \
        -H "Content-Type: application/json" \
        -d "{\"email\":\"$email\",\"password\":\"$pw\"}")
    if [ "$code" = "200" ]; then
        echo "    ✓ $label ($email) 로그인 OK"
    else
        echo "    ✗ $label ($email) 로그인 실패 (HTTP $code)"
    fi
done

# ── Discord 알림 ───────────────────────────────────────────────────────────────
echo "[6/6] Discord 알림 전송..."
notify_discord 5763719 "✅ DAST 테스트 계정 초기화 완료\n서버: $BASE_URL\nUser / Artisan / Admin 모두 인증된 경로로 생성됨"
echo "    완료"

echo ""
echo "=== 모든 작업 완료 ==="

# ── 자기 삭제 ─────────────────────────────────────────────────────────────────
rm -- "$0"
echo "스크립트 삭제 완료 ($0)"
