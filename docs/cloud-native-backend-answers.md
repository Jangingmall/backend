# 네이티브 팀 확인 요청 — 백엔드 답변

> 작성일: 2026-09-15
> 대상 브랜치: feat/docs (현재 코드베이스 기준)

---

## 1. Secret 전체 목록

PG(결제), 택배 API는 현재 미구현 — 해당 도메인 개발 시점에 추가 예정.

| 용도 | Secret 이름 (권장) | 필수 여부 |
|------|-------------------|-----------|
| DB URL | `db-url` | 필수 |
| DB Username | `db-username` | 필수 |
| DB Password | `db-password` | 필수 |
| Redis Host | `redis-host` | 필수 |
| Redis Port | `redis-port` | 선택 (기본값 6379) |
| JWT Secret | `jwt-secret` | 필수 |
| Kakao OAuth Client ID | `kakao-client-id` | 필수 |
| Kakao OAuth Client Secret | `kakao-client-secret` | 필수 |
| Naver OAuth Client ID | `naver-client-id` | 필수 |
| Naver OAuth Client Secret | `naver-client-secret` | 필수 |
| SMTP Host | `mail-host` | 필수 |
| SMTP Port | `mail-port` | 선택 (기본값 587) |
| SMTP Username | `mail-username` | 필수 |
| SMTP Password | `mail-password` | 필수 |
| 발신 이메일 주소 | `mail-from` | 필수 |
| 이메일 인증 URL | `email-verification-url` | 필수 |
| 이메일 인증 성공 리다이렉트 URL | `email-verification-success-redirect` | 필수 |
| 이메일 인증 토큰 TTL(초) | `email-verification-ttl-seconds` | 선택 (기본값 1800) |
| OAuth 프론트엔드 콜백 URL | `oauth-frontend-redirect-url` | 필수 |
| OAuth HTTP Timeout(ms) | `oauth-http-timeout-millis` | 선택 (기본값 5000) |
| AI 서버 Base URL | `ai-base-url` | 필수 |
| Rate Limit 최대 시도 횟수 | `member-rate-limit-attempts` | 선택 (기본값 10) |
| Rate Limit 윈도우(초) | `member-rate-limit-window-seconds` | 선택 (기본값 60) |
| PG Secret | — | 미구현 |
| 택배 API Key | — | 미구현 |

---

## 2. Secret별 Spring Property / Environment Variable

| 용도                  | Spring Property                                                  | Environment Variable                  |
|---------------------|------------------------------------------------------------------|---------------------------------------|
| DB URL              | `spring.datasource.url`                                          | `DB_URL`                              |
| DB Username         | `spring.datasource.username`                                     | `DB_USERNAME`                         |
| DB Password         | `spring.datasource.password`                                     | `DB_PASSWORD`                         |
| Redis Host          | `spring.data.redis.host`                                         | `REDIS_HOST`                          |
| Redis Port          | `spring.data.redis.port`                                         | `REDIS_PORT`                          |
| JWT Secret          | `jwt.secret`                                                     | `JWT_SECRET`                          |
| Kakao Client ID     | `spring.security.oauth2.client.registration.kakao.client-id`     | `KAKAO_CLIENT_ID`                     |
| Kakao Client Secret | `spring.security.oauth2.client.registration.kakao.client-secret` | `KAKAO_CLIENT_SECRET`                 |
| Naver Client ID     | `spring.security.oauth2.client.registration.naver.client-id`     | `NAVER_CLIENT_ID`                     |
| Naver Client Secret | `spring.security.oauth2.client.registration.naver.client-secret` | `NAVER_CLIENT_SECRET`                 |
| SMTP Host           | `spring.mail.host`                                               | `MAIL_HOST`                           |
| SMTP Port           | `spring.mail.port`                                               | `MAIL_PORT`                           |
| SMTP Username       | `spring.mail.username`                                           | `MAIL_USERNAME`                       |
| SMTP Password       | `spring.mail.password`                                           | `MAIL_PASSWORD`                       |
| 발신 이메일              | `member.email-verification.from`                                 | `MAIL_FROM`                           |
| 이메일 인증 URL          | `member.email-verification.verification-url`                     | `EMAIL_VERIFICATION_URL`              |
| 이메일 인증 성공 URL       | `member.email-verification.success-redirect-url`                 | `EMAIL_VERIFICATION_SUCCESS_REDIRECT` |
| 이메일 인증 TTL          | `member.email-verification.token-expiry-seconds`                 | `EMAIL_VERIFICATION_TTL_SECONDS`      |
| OAuth 콜백 URL        | `member.oauth.frontend-redirect-url`                             | `OAUTH_FRONTEND_REDIRECT_URL`         |
| OAuth HTTP Timeout  | `member.oauth.http-timeout-millis`                               | `OAUTH_HTTP_TIMEOUT_MILLIS`           |
| AI Base URL         | `ai.base-url`                                                    | `AI_BASE_URL`                         |
| Rate Limit 횟수       | `member.rate-limit.attempts`                                     | `MEMBER_RATE_LIMIT_ATTEMPTS`          |
| Rate Limit 윈도우      | `member.rate-limit.window-seconds`                               | `MEMBER_RATE_LIMIT_WINDOW_SECONDS`    |

---

## 3. Spring configtree 사용 가능 여부

**현재 상태: Environment Variable 방식 유지 필요 (수정 불필요)**

현재 `application.yml`은 `${ENV_VAR}` 플레이스홀더 방식만 사용합니다.

`SPRING_CONFIG_IMPORT=configtree:/mnt/secrets-store/` 방식을 사용하려면 configtree 파일명이 Spring Property 계층 구조를 `.`으로 구분한 형태여야 합니다.

예: `spring.datasource.password` → 파일명 `spring.datasource.password`

단, 현재 `JWT_SECRET` → `jwt.secret`, `KAKAO_CLIENT_SECRET` → `spring.security.oauth2.client.registration.kakao.client-secret`처럼 Environment Variable 이름과 Property 이름이 다릅니다.

**권장 방식:**
- Secrets Store CSI Driver → 환경변수로 주입 (`env.valueFrom.secretKeyRef`) 방식이 기존 코드 변경 없이 바로 동작
- configtree를 쓰려면 `application.yml`의 모든 `${ENV_VAR}` 참조를 `${spring.property.name}`으로 교체 필요 → 공수 발생

---

## 4. Dockerfile / Container Build

### 4-1. Dockerfile 위치

```
backend/Dockerfile
```

Multi-stage 구성:
- `builder` 스테이지: JDK 25 + Gradle → bootJar + 레이어 추출
- `ci` 타겟: GHA pre-built 레이어 COPY (CD 파이프라인용)
- `local` 타겟: Docker 내부 풀 빌드 (로컬 개발용)

CD에서는 `--target ci` 옵션으로 빌드합니다.

### 4-2. CI Build / Test 명령

```bash
# 테스트
./gradlew test --no-daemon --build-cache

# JAR 빌드
./gradlew bootJar --no-daemon --build-cache -x test

# Spring Boot 레이어 추출 (Docker 레이어 캐시 최적화)
java -Djarmode=layertools -jar build/libs/*.jar extract --destination build/extracted
```

사전 작업: 없음. Gradle Wrapper 포함되어 있어 별도 Gradle 설치 불필요.

### 4-3. Container Runtime 기준

| 항목 | 상태 |
|------|------|
| Multi-stage Build | ✅ 적용됨 |
| Build JDK 25 | ✅ `eclipse-temurin:25-jdk-noble` |
| Runtime `eclipse-temurin:25-jre` | ✅ `eclipse-temurin:25-jre-noble` |
| `linux/amd64` | ✅ (기본 빌드 타겟) |
| Non-root User | ✅ `app` 유저/그룹 생성 후 전환 |
| App Port `8080` | ✅ |
| Management Port `9090` | ✅ |
| Secret을 Image 내부에 포함하지 않음 | ✅ 모두 환경변수 참조 |
| stdout / stderr 로그 출력 | ✅ Spring Boot 기본 + ECS JSON 포맷 |

### 4-4. JVM Memory 설정

| 옵션 | 값 |
|------|----|
| `-Xms` | 미설정 (JVM 기본값) |
| `-Xmx` | 미설정 |
| `-XX:MaxRAMPercentage` | `75.0` |
| `JAVA_TOOL_OPTIONS` | 미설정 |
| 기타 | `-XX:+UseContainerSupport`, `-Djava.security.egd=file:/dev/./urandom` |

Container Memory Limit `4Gi` 기준: `-XX:MaxRAMPercentage=75.0` → 최대 힙 약 3GB.
별도 `-Xmx` 지정 없이 `UseContainerSupport`가 cgroup limit을 자동 감지합니다.

### 4-5. 추가 Runtime Dependency

없음.

- OS Package / Native Library: 없음
- Writable 디렉토리: 없음 (파일 저장 없음, 임시파일 미사용)
- Font/Image 처리: 없음
- Timezone: 별도 설정 없음 (JVM 기본 UTC)
- Startup Script: 없음

---

## 5. Backend Monitoring 구성

### 5-1. Actuator / Micrometer 구현 상태

| 항목 | 상태 |
|------|------|
| Spring Boot Actuator | ✅ 적용됨 |
| Micrometer | ✅ 적용됨 |
| Prometheus Registry | ✅ `micrometer-registry-prometheus` |
| `/actuator/prometheus` 노출 가능 | ✅ |

Actuator 포트: **9090** (`management.server.port=9090`)
노출 엔드포인트: `health`, `prometheus`

헬스체크 경로:
- `/actuator/health` (포트 9090, 상세 정보 — 인증 필요)
- `/healthz` (포트 8080, ALB용 liveness probe — 인증 불필요)

### 5-2. 기본 Metric 제공 가능 여부

Spring Boot / Micrometer 기본 Metric 전체 제공 가능합니다.

| Metric | 제공 여부 |
|--------|-----------|
| HTTP Request Count | ✅ |
| HTTP Error / 5xx | ✅ |
| Response Time / Latency | ✅ |
| JVM Heap / Non-Heap | ✅ |
| GC | ✅ |
| Thread | ✅ |
| CPU | ✅ |
| HikariCP Connection Pool | ✅ |

**백엔드팀 추가 요청 Metric (향후 반영 예정):**
- AI 서버 호출 응답시간 / 오류율 (`/ai/chat` 엔드포인트)
- 이메일 인증 발송 성공/실패 카운트
- OAuth 콜백 처리 오류율
- Rate Limit 초과 발생 카운트

### 5-3. Application Log 방식

**JSON Structured Log** — 현재 적용됨.

```yaml
# application.yml (prod profile)
logging:
  structured:
    format:
      console: ecs  # Elastic Common Schema JSON
```

ECS(Elastic Common Schema) 포맷으로 stdout 출력. Loki / CloudWatch Logs 수집 모두 호환됩니다.

### 5-4. 로그 식별값 / 민감정보

| 항목 | 포함 여부 |
|------|-----------|
| Request ID | 미포함 (MDC 미구성 — 추후 추가 가능) |
| User ID | 일부 포함 (비즈니스 로직 INFO 로그) |
| Order ID | 일부 포함 (주문 처리 INFO 로그) |
| External API 이름 | 포함 (AI 서버 호출 시 sessionId 포함) |
| Error Code | 포함 (예외 처리 WARN/ERROR 로그) |
| Exception Stacktrace | 포함 (ERROR 레벨) |
| Password / Token / Client Secret | **미포함** — 로그 출력 금지 |

### 5-5. Backend팀 자체 Monitoring 범위

**역할 분담 (기존 인프라 협의 기준):**

| 주체 | 도구 | 범위 |
|------|------|------|
| 백엔드팀 | CloudWatch + Prometheus + Grafana | 개발 단계 모니터링 (백엔드 자체 Dashboard / Alert) |
| Cloud Native팀 | Prometheus + Grafana | 전체 서비스 운영 모니터링 |
| Cloud Native팀 | CloudWatch | 솔루션 기능에 따라 선택적 사용 |

백엔드팀은 별도 EC2(`prometheus+grafana`)를 운영하며 자체 Dashboard를 관리합니다.
Grafana Dashboard 세부 구성(패널 설계, Alert 임계값 등)은 백엔드팀 담당입니다.
Cloud Native팀과 Prometheus 스크래핑 엔드포인트(`/actuator/prometheus`, 포트 9090) 및 메트릭 태그(`application` label) 규격을 공유하여 중복 수집이 가능한 구조입니다.

---

## 8. 배포 안정성

### Graceful Shutdown

**✅ 실제 적용됨**

```yaml
server:
  shutdown: graceful
spring.lifecycle:
  timeout-per-shutdown-phase: 30s
```

### Scheduler / Batch / Background Job

**없음** — `@Scheduled`, Spring Batch, Background Worker 미사용.
현재 비동기 처리: 없음. 모든 처리는 요청-응답 동기 방식.

### 장시간 Connection

**SSE 예정 (알림 도메인) — 현재 미구현**

- WebSocket: 없음
- SSE: **알림 실시간 전송용 SSE 구현 예정** (`feat/notification-sse` 브랜치)
- Streaming Response: 없음 (AI 응답은 단건 HTTP 요청/응답, 스트리밍 아님)
- Long Polling: 없음

**SSE 관련 ALB / Blue-Green 설정 요청:**
- SSE 연결 특성상 ALB Idle Timeout을 기본 60초보다 길게 설정 권장 (최소 300초)
- Blue/Green 배포 시 구버전 Pod의 SSE 연결 drain 시간 고려 필요 — `spring.lifecycle.timeout-per-shutdown-phase=30s`로 현재 30초 설정되어 있으나 SSE 구현 완료 시점에 재협의 예정
