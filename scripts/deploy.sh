#!/usr/bin/env bash
# CD SSM에서 매번 실행 — 초기 기동 + 재배포 통합
# 컨테이너가 없으면 최초 기동, 있으면 전략에 따라 유지/재시작
set -euo pipefail

IMAGE_URI="${1:?IMAGE_URI 인자가 필요합니다}"
ECR_REGISTRY="${2:?ECR_REGISTRY 인자가 필요합니다}"
AWS_REGION="${3:?AWS_REGION 인자가 필요합니다}"
REPO_DIR="${4:-/opt/backend}"
SSM=/prod/backend

GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'
info() { echo -e "${GREEN}[INFO]${NC}  $*"; }
warn() { echo -e "${YELLOW}[WARN]${NC}  $*"; }

# ── SSM에서 값 조회 ───────────────────────────────────────────────────────────
ssm_get() {
    aws ssm get-parameter \
        --region "${AWS_REGION}" \
        --name "$1" \
        --with-decryption \
        --query "Parameter.Value" \
        --output text
}

# ── 설정 파일 해시 비교 ───────────────────────────────────────────────────────
files_changed() {
    [[ ! -f "$2" ]] && return 0
    [[ "$(sha256sum "$1" | awk '{print $1}')" != "$(sha256sum "$2" | awk '{print $1}')" ]]
}

# ── 컨테이너 존재 여부 확인 ───────────────────────────────────────────────────
container_exists() {
    docker ps -a --format '{{.Names}}' | grep -q "^${1}$"
}

# ── 디렉토리 초기화 (멱등) ────────────────────────────────────────────────────
mkdir -p /data/postgres /data/redis /data/grafana /etc/backend
mkdir -p /etc/monitoring/prometheus
mkdir -p /etc/monitoring/grafana/provisioning/dashboards
mkdir -p /etc/monitoring/grafana/provisioning/datasources
mkdir -p /var/lib/grafana/dashboards
chmod 700 /etc/backend

# ── monitoring 설정 동기화 ────────────────────────────────────────────────────
APP_EC2_INTERNAL_IP=$(ssm_get "${SSM}/app-ec2-internal-ip")
sed "s/<APP_EC2_INTERNAL_IP>/${APP_EC2_INTERNAL_IP}/g" \
    "${REPO_DIR}/monitoring/prometheus/prometheus.prod.yml" \
    > /tmp/prometheus.yml.new

cp "${REPO_DIR}/monitoring/grafana/provisioning/dashboards/default.yaml" \
    /etc/monitoring/grafana/provisioning/dashboards/
cp "${REPO_DIR}/monitoring/grafana/provisioning/datasources/prometheus.yaml" \
    /etc/monitoring/grafana/provisioning/datasources/

# ── ECR 로그인 ────────────────────────────────────────────────────────────────
info "ECR 로그인..."
aws ecr get-login-password --region "${AWS_REGION}" \
    | docker login --username AWS --password-stdin "${ECR_REGISTRY}"

# ── PostgreSQL — 없으면 최초 기동, 있으면 유지 ───────────────────────────────
if container_exists db; then
    info "db 컨테이너 유지 (DB는 CD에서 중지하지 않음)"
else
    info "PostgreSQL 최초 기동..."
    DB_PW_FILE=$(mktemp); chmod 600 "${DB_PW_FILE}"
    ssm_get "${SSM}/db-password" > "${DB_PW_FILE}"

    docker run -d \
        --name db \
        --restart unless-stopped \
        -e POSTGRES_DB=jangingmall \
        -e POSTGRES_USER="$(ssm_get "${SSM}/db-username")" \
        -e POSTGRES_PASSWORD_FILE=/run/secrets/db_password \
        -v "${DB_PW_FILE}:/run/secrets/db_password:ro" \
        -v /data/postgres:/var/lib/postgresql/data \
        -p 127.0.0.1:5432:5432 \
        postgres:16-alpine

    for i in $(seq 1 30); do
        if docker exec db pg_isready -U postgres &>/dev/null; then
            info "PostgreSQL 준비 완료"; rm -f "${DB_PW_FILE}"; break
        fi
        [[ $i -eq 30 ]] && { rm -f "${DB_PW_FILE}"; echo "[ERROR] PostgreSQL 타임아웃" >&2; exit 1; }
        sleep 1
    done
fi

# ── Redis — 없으면 최초 기동, 설정 변경 시 재시작 ────────────────────────────
REDIS_CONF_SRC="${REPO_DIR}/monitoring/redis/redis.conf"
REDIS_CONF_DST="/etc/monitoring/redis/redis.conf"

if ! container_exists redis; then
    info "Redis 최초 기동..."
    docker run -d \
        --name redis \
        --restart unless-stopped \
        -v /data/redis:/data \
        -p 127.0.0.1:6379:6379 \
        redis:7-alpine \
        redis-server --appendonly yes
elif [[ -f "${REDIS_CONF_SRC}" ]] && files_changed "${REDIS_CONF_SRC}" "${REDIS_CONF_DST}"; then
    warn "Redis 설정 변경 — 재시작"
    mkdir -p /etc/monitoring/redis
    cp "${REDIS_CONF_SRC}" "${REDIS_CONF_DST}"
    docker stop redis && docker rm redis
    docker run -d \
        --name redis \
        --restart unless-stopped \
        -v /data/redis:/data \
        -p 127.0.0.1:6379:6379 \
        redis:7-alpine \
        redis-server /etc/monitoring/redis/redis.conf
else
    info "Redis 유지"
fi

# ── Prometheus — 없으면 최초 기동, 설정 변경 시 재시작 ───────────────────────
PROM_DST="/etc/monitoring/prometheus/prometheus.yml"

if ! container_exists prometheus; then
    info "Prometheus 최초 기동..."
    mv /tmp/prometheus.yml.new "${PROM_DST}"
    docker run -d \
        --name prometheus \
        --restart unless-stopped \
        --network host \
        -v "${PROM_DST}:/etc/prometheus/prometheus.yml:ro" \
        prom/prometheus:latest
elif files_changed /tmp/prometheus.yml.new "${PROM_DST}"; then
    warn "Prometheus 설정 변경 — 재시작"
    mv /tmp/prometheus.yml.new "${PROM_DST}"
    docker restart prometheus
else
    info "Prometheus 유지"; rm -f /tmp/prometheus.yml.new
fi

# ── Grafana — 없으면 최초 기동, 설정 변경 시 재시작 ─────────────────────────
GRAFANA_DASHBOARD_SRC="${REPO_DIR}/monitoring/grafana/dashboards/backend-overview.json"
GRAFANA_DASHBOARD_DST="/var/lib/grafana/dashboards/backend-overview.json"

if ! container_exists grafana; then
    info "Grafana 최초 기동..."
    cp "${GRAFANA_DASHBOARD_SRC}" "${GRAFANA_DASHBOARD_DST}"

    GF_ENV=$(mktemp); chmod 600 "${GF_ENV}"
    printf 'GF_SECURITY_ADMIN_USER=admin\nGF_SECURITY_ADMIN_PASSWORD=%s\n' \
        "$(ssm_get "${SSM}/grafana-admin-password")" > "${GF_ENV}"

    docker run -d \
        --name grafana \
        --restart unless-stopped \
        --env-file "${GF_ENV}" \
        -v /data/grafana:/var/lib/grafana \
        -v /etc/monitoring/grafana/provisioning:/etc/grafana/provisioning:ro \
        -v /var/lib/grafana/dashboards:/var/lib/grafana/dashboards:ro \
        -p 3000:3000 \
        grafana/grafana:latest
    rm -f "${GF_ENV}"
else
    GRAFANA_CHANGED=false
    if files_changed "${GRAFANA_DASHBOARD_SRC}" "${GRAFANA_DASHBOARD_DST}"; then
        cp "${GRAFANA_DASHBOARD_SRC}" "${GRAFANA_DASHBOARD_DST}"
        GRAFANA_CHANGED=true
    fi
    if files_changed \
        "${REPO_DIR}/monitoring/grafana/provisioning/datasources/prometheus.yaml" \
        "/etc/monitoring/grafana/provisioning/datasources/prometheus.yaml"; then
        GRAFANA_CHANGED=true
    fi
    if [[ "${GRAFANA_CHANGED}" == true ]]; then
        warn "Grafana 설정 변경 — 재시작"; docker restart grafana
    else
        info "Grafana 유지"
    fi
fi

# ── backend — 항상 재배포 ─────────────────────────────────────────────────────
info "backend pull: ${IMAGE_URI}"
docker pull "${IMAGE_URI}"
docker stop backend 2>/dev/null || true
docker rm   backend 2>/dev/null || true
docker run -d \
    --name backend \
    --restart unless-stopped \
    --memory 1536m \
    -p 8080:8080 \
    -p 9090:9090 \
    --env-file /etc/backend/.env \
    "${IMAGE_URI}"

info "backend healthcheck 대기..."
for i in $(seq 1 24); do
    if curl -sf http://localhost:8080/healthz &>/dev/null; then
        info "backend 기동 확인 (${i}번째)"; break
    fi
    [[ $i -eq 24 ]] && { docker logs --tail 50 backend >&2; echo "[ERROR] healthcheck 타임아웃" >&2; exit 1; }
    sleep 5
done

# ── 미사용 이미지 정리 ────────────────────────────────────────────────────────
docker image prune -f &>/dev/null || true

info "========================================"
info "배포 완료: ${IMAGE_URI}"
info "========================================"
