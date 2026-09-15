# 배포 환경변수 계약서 (백엔드 ↔ 인프라)

> 작성: 강정훈 | 최종 업데이트: 2026-09-15
> Parameter Store 경로 규칙: `/prod/backend/{KEY}`

---

## 주입 방식

인프라팀이 ECS Task Definition (또는 EC2 UserData)에서 AWS SSM Parameter Store 값을 환경변수로 주입한다.  
백엔드 애플리케이션은 `${ENV_VAR}` 플레이스홀더로 읽으며, Spring Cloud AWS 등 별도 의존성은 사용하지 않는다.

---

## 필수 환경변수 목록

| 환경변수 | SSM 경로 | 설명 | 예시 |
|---|---|---|---|
| `SPRING_PROFILES_ACTIVE` | (인프라 직접 설정) | 활성 프로파일 | `prod` |
| `DB_URL` | `/prod/backend/db-url` | PostgreSQL JDBC URL | `jdbc:postgresql://db.internal:5432/jangingmall` |
| `DB_USERNAME` | `/prod/backend/db-username` | DB 사용자명 | `jangingmall` |
| `DB_PASSWORD` | `/prod/backend/db-password` | DB 비밀번호 | *(SecureString)* |
| `REDIS_HOST` | `/prod/backend/redis-host` | Redis 호스트 | `redis.internal` |
| `REDIS_PORT` | `/prod/backend/redis-port` | Redis 포트 (기본 6379) | `6379` |
| `JWT_SECRET` | `/prod/backend/jwt-secret` | JWT 서명 키 (256bit 이상) | *(SecureString)* |
| `MAIL_HOST` | `/prod/backend/mail-host` | SMTP 서버 호스트 | `email-smtp.ap-northeast-2.amazonaws.com` |
| `MAIL_PORT` | `/prod/backend/mail-port` | SMTP 포트 (기본 587) | `587` |
| `MAIL_USERNAME` | `/prod/backend/mail-username` | SMTP 사용자 | *(SecureString)* |
| `MAIL_PASSWORD` | `/prod/backend/mail-password` | SMTP 비밀번호 | *(SecureString)* |
| `MAIL_FROM` | `/prod/backend/mail-from` | 발신 이메일 주소 | `no-reply@midam.store` |
| `KAKAO_CLIENT_ID` | `/prod/backend/kakao-client-id` | 카카오 OAuth2 Client ID | *(SecureString)* |
| `KAKAO_CLIENT_SECRET` | `/prod/backend/kakao-client-secret` | 카카오 OAuth2 Client Secret | *(SecureString)* |
| `NAVER_CLIENT_ID` | `/prod/backend/naver-client-id` | 네이버 OAuth2 Client ID | *(SecureString)* |
| `NAVER_CLIENT_SECRET` | `/prod/backend/naver-client-secret` | 네이버 OAuth2 Client Secret | *(SecureString)* |
| `EMAIL_VERIFICATION_URL` | `/prod/backend/email-verification-url` | 이메일 인증 콜백 URL | `https://api.midam.store/api/member/email-verifications/verify` |
| `EMAIL_VERIFICATION_SUCCESS_REDIRECT` | `/prod/backend/email-verification-success-redirect` | 인증 완료 후 리다이렉트 URL | `https://midam.store/` |
| `EMAIL_VERIFICATION_TTL_SECONDS` | `/prod/backend/email-verification-ttl-seconds` | 이메일 인증 토큰 유효 시간(초) | `1800` |
| `OAUTH_FRONTEND_REDIRECT_URL` | `/prod/backend/oauth-frontend-redirect-url` | OAuth 완료 후 프론트 리다이렉트 URL | `https://midam.store/oauth/callback` |
| `OAUTH_HTTP_TIMEOUT_MILLIS` | `/prod/backend/oauth-http-timeout-millis` | OAuth 외부 HTTP 타임아웃(ms) | `5000` |
| `AI_BASE_URL` | `/prod/backend/ai-base-url` | AI 서버 내부 URL | `http://ai.internal:8001` |
| `MEMBER_RATE_LIMIT_ATTEMPTS` | `/prod/backend/member-rate-limit-attempts` | 회원 요청 제한 횟수 | `10` |
| `MEMBER_RATE_LIMIT_WINDOW_SECONDS` | `/prod/backend/member-rate-limit-window-seconds` | 회원 요청 제한 윈도우(초) | `60` |

---

## 포트 계약

| 포트 | 용도 | 외부 노출 |
|---|---|---|
| `8080` | 애플리케이션 (REST API, ALB 타겟) | ALB를 통해 HTTPS 443으로 노출 |
| `9090` | Actuator 관리 포트 (Prometheus 스크래핑, Health) | 인프라 내부망만 허용 (Security Group 제한) |

---

## 헬스체크 엔드포인트

| 경로 | 포트 | 용도 |
|---|---|---|
| `/healthz` | `8080` | ALB 헬스체크 (ping group) |
| `/actuator/health` | `9090` | 전체 헬스 |
| `/actuator/health/liveness` | `9090` | Liveness probe |
| `/actuator/health/readiness` | `9090` | Readiness probe |
| `/actuator/prometheus` | `9090` | Prometheus 메트릭 스크래핑 |

---

## 팀별 책임 경계

| 팀 | 책임 |
|---|---|
| **백엔드** | 환경변수 목록 정의·유지, `${ENV_VAR}` 플레이스홀더 사용, Dockerfile 제공 |
| **인프라** | Parameter Store 값 등록, ECS Task Definition / EC2 UserData에서 env 주입, Security Group 구성 |
| **프론트엔드** | `OAUTH_FRONTEND_REDIRECT_URL`, `EMAIL_VERIFICATION_SUCCESS_REDIRECT` URL 최종 확정 후 인프라에 전달 |
| **AI** | `AI_BASE_URL` (내부망 주소) 확정 후 인프라에 전달 |

---

## 환경변수 변경 절차

1. 백엔드팀이 `application.yml`에 새 `${NEW_VAR}` 플레이스홀더 추가 후 이 문서 업데이트
2. 인프라팀이 `/prod/backend/new-var` Parameter Store 항목 생성
3. ECS Task Definition 또는 EC2 환경에 변수 반영 후 배포
