#!/usr/bin/env bash
# EC2 최초 1회 실행 — 인프라 컨테이너 기동
# 모든 민감 값은 SSM Parameter Store에서 직접 조회 (평문 노출 없음)
# 사용법: sudo bash setup.sh
set -euo pipefail

GREEN='\033[0;32m'; YELLOW='\033[1;33m'; RED='\033[0;31m'; NC='\033[0m'
info()  { echo -e "${GREEN}[INFO]${NC}  $*"; }
warn()  { echo -e "${YELLOW}[WARN]${NC}  $*"; }
error() { echo -e "${RED}[ERROR]${NC} $*"; exit 1; }

: "${AWS_REGION:=ap-northeast-2}"
: "${REPO_DIR:=/opt/backend}"
SSM=/prod/backend

# ── SSM에서 값 조회 (SecureString 포함) ──────────────────────────────────────
ssm_get() {
    aws ssm get-parameter \
        --region "${AWS_REGION}" \
        --name "$1" \
        --with-decryption \
        --query "Parameter.Value" \
        --output text
}

info "SSM에서 설정 값 조회..."
DB_NAME="jangingmall"
DB_USER=$(ssm_get "${SSM}/db-username")
DB_PASSWORD=$(ssm_get "${SSM}/db-password")
APP_EC2_INTERNAL_IP=$(ssm_get "${SSM}/app-ec2-internal-ip")
GRAFANA_ADMIN_USER="admin"
GRAFANA_ADMIN_PASSWORD=$(ssm_get "${SSM}/grafana-admin-password")

# ── Docker 설치 확인 ──────────────────────────────────────────────────────────
info "Docker 설치 확인..."
if ! command -v docker &>/dev/null; then
    info "Docker 설치 중..."
    apt-get update -q
    apt-get install -y -q ca-certificates curl gnupg
    install -m 0755 -d /etc/apt/keyrings
    curl -fsSL https://download.docker.com/linux/ubuntu/gpg \
        | gpg --dearmor -o /etc/apt/keyrings/docker.gpg
    echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] \
        https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" \
        > /etc/apt/sources.list.d/docker.list
    apt-get update -q
    apt-get install -y -q docker-ce docker-ce-cli containerd.io docker-compose-plugin
    systemctl enable docker && systemctl start docker
    info "Docker 설치 완료"
else
    info "Docker 이미 설치됨: $(docker --version)"
fi

# ── 디렉토리 구성 ─────────────────────────────────────────────────────────────
info "디렉토리 구성..."
mkdir -p /data/postgres /data/redis /data/grafana
mkdir -p /etc/backend
mkdir -p /etc/monitoring/prometheus
mkdir -p /etc/monitoring/grafana/provisioning/dashboards
mkdir -p /etc/monitoring/grafana/provisioning/datasources
mkdir -p /var/lib/grafana/dashboards
chmod 700 /etc/backend

# ── .env 파일 확인 ────────────────────────────────────────────────────────────
[[ ! -f /etc/backend/.env ]] && warn "/etc/backend/.env 없음 — CD 실행 전 반드시 준비하세요."

# ── monitoring 설정 파일 복사 ─────────────────────────────────────────────────
info "모니터링 설정 파일 복사..."
sed "s/<APP_EC2_INTERNAL_IP>/${APP_EC2_INTERNAL_IP}/g" \
    "${REPO_DIR}/monitoring/prometheus/prometheus.prod.yml" \
    > /etc/monitoring/prometheus/prometheus.yml

cp "${REPO_DIR}/monitoring/grafana/provisioning/dashboards/default.yaml" \
    /etc/monitoring/grafana/provisioning/dashboards/
cp "${REPO_DIR}/monitoring/grafana/provisioning/datasources/prometheus.yaml" \
    /etc/monitoring/grafana/provisioning/datasources/
cp "${REPO_DIR}/monitoring/grafana/dashboards/backend-overview.json" \
    /var/lib/grafana/dashboards/

# ── PostgreSQL — docker secret으로 패스워드 주입 (docker inspect 노출 차단) ──
if docker ps -a --format '{{.Names}}' | grep -q '^db$'; then
    warn "db 컨테이너 이미 존재 — 건너뜀 (DB는 직접 관리)"
else
    info "PostgreSQL 최초 기동..."
    echo "${DB_PASSWORD}" | docker secret create db_password - 2>/dev/null || true
    # secret 미지원 환경(standalone)은 tmpfs 파일로 안전 주입
    DB_PW_FILE=$(mktemp)
    echo -n "${DB_PASSWORD}" > "${DB_PW_FILE}"
    chmod 600 "${DB_PW_FILE}"

    docker run -d \
        --name db \
        --restart unless-stopped \
        -e POSTGRES_DB="${DB_NAME}" \
        -e POSTGRES_USER="${DB_USER}" \
        -e POSTGRES_PASSWORD_FILE=/run/secrets/db_password \
        -v "${DB_PW_FILE}:/run/secrets/db_password:ro" \
        -v /data/postgres:/var/lib/postgresql/data \
        -p 127.0.0.1:5432:5432 \
        postgres:16-alpine

    for i in $(seq 1 30); do
        if docker exec db pg_isready -U "${DB_USER}" -d "${DB_NAME}" &>/dev/null; then
            info "PostgreSQL 준비 완료"
            rm -f "${DB_PW_FILE}"
            break
        fi
        [[ $i -eq 30 ]] && { rm -f "${DB_PW_FILE}"; error "PostgreSQL 초기화 타임아웃"; }
        sleep 1
    done
fi

# ── Redis ─────────────────────────────────────────────────────────────────────
if docker ps -a --format '{{.Names}}' | grep -q '^redis$'; then
    warn "redis 컨테이너 이미 존재 — 건너뜀"
else
    info "Redis 기동..."
    docker run -d \
        --name redis \
        --restart unless-stopped \
        -v /data/redis:/data \
        -p 127.0.0.1:6379:6379 \
        redis:7-alpine \
        redis-server --appendonly yes
fi

# ── Prometheus ────────────────────────────────────────────────────────────────
if docker ps -a --format '{{.Names}}' | grep -q '^prometheus$'; then
    warn "prometheus 컨테이너 이미 존재 — 건너뜀"
else
    info "Prometheus 기동..."
    docker run -d \
        --name prometheus \
        --restart unless-stopped \
        --network host \
        -v /etc/monitoring/prometheus/prometheus.yml:/etc/prometheus/prometheus.yml:ro \
        prom/prometheus:latest
fi

# ── Grafana — 패스워드를 env file로 주입 (docker inspect 노출 차단) ───────────
if docker ps -a --format '{{.Names}}' | grep -q '^grafana$'; then
    warn "grafana 컨테이너 이미 존재 — 건너뜀"
else
    info "Grafana 기동..."
    GF_ENV_FILE=$(mktemp)
    chmod 600 "${GF_ENV_FILE}"
    cat > "${GF_ENV_FILE}" << ENVEOF
GF_SECURITY_ADMIN_USER=${GRAFANA_ADMIN_USER}
GF_SECURITY_ADMIN_PASSWORD=${GRAFANA_ADMIN_PASSWORD}
ENVEOF

    docker run -d \
        --name grafana \
        --restart unless-stopped \
        --env-file "${GF_ENV_FILE}" \
        -v /data/grafana:/var/lib/grafana \
        -v /etc/monitoring/grafana/provisioning:/etc/grafana/provisioning:ro \
        -v /var/lib/grafana/dashboards:/var/lib/grafana/dashboards:ro \
        -p 3000:3000 \
        grafana/grafana:latest

    rm -f "${GF_ENV_FILE}"
fi

# ── 완료 ──────────────────────────────────────────────────────────────────────
info "========================================"
info "setup 완료"
info "  PostgreSQL : 127.0.0.1:5432 (외부 차단)"
info "  Redis      : 127.0.0.1:6379 (외부 차단)"
info "  Prometheus : http://<EC2_IP>:9090"
info "  Grafana    : http://<EC2_IP>:3000"
info "========================================"
info "다음: /etc/backend/.env 준비 후 CD 실행"
