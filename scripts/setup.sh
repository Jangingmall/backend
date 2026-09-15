#!/usr/bin/env bash
# EC2 최초 1회 실행 — DB 초기화 및 인프라 컨테이너 기동
# 사용법: sudo bash setup.sh
set -euo pipefail

# ── 색상 출력 ────────────────────────────────────────────────────────────────
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'
info()  { echo -e "${GREEN}[INFO]${NC}  $*"; }
warn()  { echo -e "${YELLOW}[WARN]${NC}  $*"; }
error() { echo -e "${RED}[ERROR]${NC} $*"; exit 1; }

# ── 필수 변수 확인 ────────────────────────────────────────────────────────────
: "${DB_NAME:=jangingmall}"
: "${DB_USER:=postgresql}"
: "${DB_PASSWORD:?DB_PASSWORD 환경변수를 설정하세요}"
: "${GRAFANA_ADMIN_USER:=admin}"
: "${GRAFANA_ADMIN_PASSWORD:?GRAFANA_ADMIN_PASSWORD 환경변수를 설정하세요}"
: "${APP_EC2_INTERNAL_IP:?APP_EC2_INTERNAL_IP 환경변수를 설정하세요}"
: "${REPO_DIR:=/opt/backend}"

# ── Docker 설치 확인 ──────────────────────────────────────────────────────────
info "Docker 설치 확인..."
if ! command -v docker &>/dev/null; then
    info "Docker 설치 중..."
    apt-get update -q
    apt-get install -y -q ca-certificates curl gnupg
    install -m 0755 -d /etc/apt/keyrings
    curl -fsSL https://download.docker.com/linux/ubuntu/gpg | gpg --dearmor -o /etc/apt/keyrings/docker.gpg
    echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] \
        https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" \
        > /etc/apt/sources.list.d/docker.list
    apt-get update -q
    apt-get install -y -q docker-ce docker-ce-cli containerd.io docker-compose-plugin
    systemctl enable docker
    systemctl start docker
    info "Docker 설치 완료"
else
    info "Docker 이미 설치됨: $(docker --version)"
fi

# ── 디렉토리 구성 ─────────────────────────────────────────────────────────────
info "디렉토리 구성..."
mkdir -p /data/postgres
mkdir -p /data/redis
mkdir -p /data/grafana
mkdir -p /etc/backend
mkdir -p /etc/monitoring/prometheus
mkdir -p /etc/monitoring/grafana/provisioning/dashboards
mkdir -p /etc/monitoring/grafana/provisioning/datasources
mkdir -p /var/lib/grafana/dashboards
chmod 700 /etc/backend

# ── .env 파일 확인 ────────────────────────────────────────────────────────────
if [[ ! -f /etc/backend/.env ]]; then
    warn "/etc/backend/.env 파일이 없습니다. SSM Parameter Store에서 값을 주입하거나 직접 생성하세요."
    warn "배포 전 반드시 /etc/backend/.env 가 존재해야 합니다."
fi

# ── monitoring 설정 파일 복사 ─────────────────────────────────────────────────
info "모니터링 설정 파일 복사..."

# prometheus.prod.yml — APP_EC2_INTERNAL_IP 치환
sed "s/<APP_EC2_INTERNAL_IP>/${APP_EC2_INTERNAL_IP}/g" \
    "${REPO_DIR}/monitoring/prometheus/prometheus.prod.yml" \
    > /etc/monitoring/prometheus/prometheus.yml

cp "${REPO_DIR}/monitoring/grafana/provisioning/dashboards/default.yaml" \
    /etc/monitoring/grafana/provisioning/dashboards/
cp "${REPO_DIR}/monitoring/grafana/provisioning/datasources/prometheus.yaml" \
    /etc/monitoring/grafana/provisioning/datasources/
cp "${REPO_DIR}/monitoring/grafana/dashboards/backend-overview.json" \
    /var/lib/grafana/dashboards/

# ── PostgreSQL 기동 ───────────────────────────────────────────────────────────
if docker ps -a --format '{{.Names}}' | grep -q '^db$'; then
    warn "db 컨테이너가 이미 존재합니다. 건너뜁니다. (DB는 직접 관리)"
else
    info "PostgreSQL 컨테이너 최초 기동..."
    docker run -d \
        --name db \
        --restart unless-stopped \
        -e POSTGRES_DB="${DB_NAME}" \
        -e POSTGRES_USER="${DB_USER}" \
        -e POSTGRES_PASSWORD="${DB_PASSWORD}" \
        -v /data/postgres:/var/lib/postgresql/data \
        -p 5432:5432 \
        postgres:16-alpine

    info "PostgreSQL 초기화 대기 중..."
    for i in $(seq 1 30); do
        if docker exec db pg_isready -U "${DB_USER}" -d "${DB_NAME}" &>/dev/null; then
            info "PostgreSQL 준비 완료"
            break
        fi
        if [[ $i -eq 30 ]]; then
            error "PostgreSQL 초기화 타임아웃 (30초)"
        fi
        sleep 1
    done
fi

# ── Redis 기동 ────────────────────────────────────────────────────────────────
if docker ps -a --format '{{.Names}}' | grep -q '^redis$'; then
    warn "redis 컨테이너가 이미 존재합니다. 건너뜁니다."
else
    info "Redis 컨테이너 기동..."
    docker run -d \
        --name redis \
        --restart unless-stopped \
        -v /data/redis:/data \
        -p 6379:6379 \
        redis:7-alpine \
        redis-server --appendonly yes
fi

# ── Prometheus 기동 ───────────────────────────────────────────────────────────
if docker ps -a --format '{{.Names}}' | grep -q '^prometheus$'; then
    warn "prometheus 컨테이너가 이미 존재합니다. 건너뜁니다."
else
    info "Prometheus 컨테이너 기동..."
    docker run -d \
        --name prometheus \
        --restart unless-stopped \
        --network host \
        -v /etc/monitoring/prometheus/prometheus.yml:/etc/prometheus/prometheus.yml:ro \
        prom/prometheus:latest
fi

# ── Grafana 기동 ──────────────────────────────────────────────────────────────
if docker ps -a --format '{{.Names}}' | grep -q '^grafana$'; then
    warn "grafana 컨테이너가 이미 존재합니다. 건너뜁니다."
else
    info "Grafana 컨테이너 기동..."
    docker run -d \
        --name grafana \
        --restart unless-stopped \
        -e GF_SECURITY_ADMIN_USER="${GRAFANA_ADMIN_USER}" \
        -e GF_SECURITY_ADMIN_PASSWORD="${GRAFANA_ADMIN_PASSWORD}" \
        -v /data/grafana:/var/lib/grafana \
        -v /etc/monitoring/grafana/provisioning:/etc/grafana/provisioning:ro \
        -v /var/lib/grafana/dashboards:/var/lib/grafana/dashboards:ro \
        -p 3000:3000 \
        grafana/grafana:latest
fi

# ── 완료 ──────────────────────────────────────────────────────────────────────
info "========================================"
info "setup 완료"
info "  PostgreSQL : localhost:5432"
info "  Redis      : localhost:6379"
info "  Prometheus : http://<EC2_IP>:9091"
info "  Grafana    : http://<EC2_IP>:3000  (${GRAFANA_ADMIN_USER} / ***)"
info "========================================"
info "다음 단계: /etc/backend/.env 파일을 채운 후 CD 파이프라인을 실행하세요."
