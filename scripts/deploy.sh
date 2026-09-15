#!/usr/bin/env bash
# CD SSM에서 매번 실행 — backend 재배포 + 변경 시 인프라 컨테이너 재시작
# 사용법: bash deploy.sh <IMAGE_URI> <ECR_REGISTRY> <AWS_REGION> <REPO_DIR>
set -euo pipefail

IMAGE_URI="${1:?IMAGE_URI 인자가 필요합니다}"
ECR_REGISTRY="${2:?ECR_REGISTRY 인자가 필요합니다}"
AWS_REGION="${3:?AWS_REGION 인자가 필요합니다}"
REPO_DIR="${4:-/opt/backend}"

GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'
info() { echo -e "${GREEN}[INFO]${NC}  $*"; }
warn() { echo -e "${YELLOW}[WARN]${NC}  $*"; }

# ── ECR 로그인 ────────────────────────────────────────────────────────────────
info "ECR 로그인..."
aws ecr get-login-password --region "${AWS_REGION}" \
    | docker login --username AWS --password-stdin "${ECR_REGISTRY}"

# ── backend (JAR) — 항상 재배포 ──────────────────────────────────────────────
info "backend 이미지 pull: ${IMAGE_URI}"
docker pull "${IMAGE_URI}"

info "backend 컨테이너 교체..."
docker stop backend 2>/dev/null || true
docker rm   backend 2>/dev/null || true
docker run -d \
    --name backend \
    --restart unless-stopped \
    -p 8080:8080 \
    -p 9090:9090 \
    --env-file /etc/backend/.env \
    "${IMAGE_URI}"

# ── healthcheck — backend 기동 확인 ──────────────────────────────────────────
info "backend healthcheck 대기..."
for i in $(seq 1 24); do
    if curl -sf http://localhost:8080/healthz &>/dev/null; then
        info "backend 정상 기동 확인 (${i}번째 시도)"
        break
    fi
    if [[ $i -eq 24 ]]; then
        echo "[ERROR] backend healthcheck 타임아웃 (120초)" >&2
        docker logs --tail 50 backend >&2
        exit 1
    fi
    sleep 5
done

# ── 설정 파일 해시 비교 함수 ──────────────────────────────────────────────────
files_changed() {
    local src="$1"
    local dst="$2"
    [[ ! -f "$dst" ]] && return 0
    [[ "$(sha256sum "$src" | awk '{print $1}')" != "$(sha256sum "$dst" | awk '{print $1}')" ]]
}

# ── Redis — appendonly.aof 설정 변경 시만 재시작 ─────────────────────────────
# Redis는 데이터 보존이 중요하므로 설정 변경 시에만 graceful restart
REDIS_CONF_SRC="${REPO_DIR}/monitoring/redis/redis.conf"
REDIS_CONF_DST="/etc/monitoring/redis/redis.conf"
if [[ -f "${REDIS_CONF_SRC}" ]] && files_changed "${REDIS_CONF_SRC}" "${REDIS_CONF_DST}"; then
    warn "Redis 설정 변경 감지 — 재시작"
    mkdir -p /etc/monitoring/redis
    cp "${REDIS_CONF_SRC}" "${REDIS_CONF_DST}"
    docker stop redis 2>/dev/null || true
    docker rm   redis 2>/dev/null || true
    docker run -d \
        --name redis \
        --restart unless-stopped \
        -v /data/redis:/data \
        -p 6379:6379 \
        redis:7-alpine \
        redis-server /etc/monitoring/redis/redis.conf
    info "Redis 재시작 완료"
else
    info "Redis 설정 변경 없음 — 유지"
fi

# ── Prometheus — prometheus.yml 변경 시만 재시작 ─────────────────────────────
PROM_SRC="${REPO_DIR}/monitoring/prometheus/prometheus.prod.yml"
PROM_DST="/etc/monitoring/prometheus/prometheus.yml"
if files_changed "${PROM_SRC}" "${PROM_DST}"; then
    warn "Prometheus 설정 변경 감지 — 재시작"
    cp "${PROM_SRC}" "${PROM_DST}"
    docker restart prometheus
    info "Prometheus 재시작 완료"
else
    info "Prometheus 설정 변경 없음 — 유지"
fi

# ── Grafana — 대시보드 or provisioning 변경 시만 재시작 ──────────────────────
GRAFANA_DASHBOARD_SRC="${REPO_DIR}/monitoring/grafana/dashboards/backend-overview.json"
GRAFANA_DASHBOARD_DST="/var/lib/grafana/dashboards/backend-overview.json"
GRAFANA_DS_SRC="${REPO_DIR}/monitoring/grafana/provisioning/datasources/prometheus.yaml"
GRAFANA_DS_DST="/etc/monitoring/grafana/provisioning/datasources/prometheus.yaml"

GRAFANA_CHANGED=false
if files_changed "${GRAFANA_DASHBOARD_SRC}" "${GRAFANA_DASHBOARD_DST}"; then
    cp "${GRAFANA_DASHBOARD_SRC}" "${GRAFANA_DASHBOARD_DST}"
    GRAFANA_CHANGED=true
fi
if files_changed "${GRAFANA_DS_SRC}" "${GRAFANA_DS_DST}"; then
    cp "${GRAFANA_DS_SRC}" "${GRAFANA_DS_DST}"
    GRAFANA_CHANGED=true
fi

if [[ "${GRAFANA_CHANGED}" == true ]]; then
    warn "Grafana 설정 변경 감지 — 재시작"
    docker restart grafana
    info "Grafana 재시작 완료"
else
    info "Grafana 설정 변경 없음 — 유지"
fi

# ── 사용하지 않는 이미지 정리 ────────────────────────────────────────────────
docker image prune -f &>/dev/null || true

info "========================================"
info "배포 완료: ${IMAGE_URI}"
info "========================================"
