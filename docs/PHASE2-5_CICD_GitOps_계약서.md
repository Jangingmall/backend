# PHASE2-5 CI/CD · EKS GitOps 전환 협업 계약서

> 기준일: 2026-09-18  
> 상태: **Workflow 수정 완료 / AWS 값 미수령**  
> 전달 출처: 인프라팀 (Backend CI/CD · EKS GitOps 전환 확정 및 협업 요청사항)

---

## 1. 배포 구조 (확정)

```
feat/*, fix/*
      ↓ PR
   develop
      ↓ merge
CI + Docker Build
      ↓
GitHub OIDC
      ↓
ECR Push
      ↓
image digest 획득
      ↓
infra Stage PR 생성 (← Native팀 접점 연결 후 추가)
      ↓
infra/main Merge
      ↓
Stage Argo CD Auto Sync
      ↓
Stage EKS
      ↓
Blue/Green Preview 검증
```

---

## 2. 브랜치 전략 (확정)

| 브랜치 | 역할 | CI/CD 동작 |
|--------|------|-----------|
| `feat/*`, `fix/*` | 기능 개발 | CI: Test + Docker Build 검증 (push 없음) |
| `develop` | 통합 브랜치 | CD: Test → Docker Build → ECR Push → digest 산출 |
| `release/vX.Y.Z` | 배포 후보 | **Image 재빌드 금지** — Stage digest를 그대로 Prod 승격 |
| `main` | 출시 이력 | Prod 배포 완료 후 반영 |

**핵심 원칙:** `release` 브랜치에서 Docker Image를 다시 Build하지 않는다. Stage에서 검증한 동일 digest를 Prod overlay에 승격한다.

---

## 3. CI 계약 (`ci.yml`)

### 트리거

| 이벤트 | 대상 브랜치 |
|--------|-----------|
| `push` | `feat/**`, `fix/**`, `hotfix/**` |
| `pull_request` | `develop` |

### Job 구성

| Job | 조건 | 내용 |
|-----|------|------|
| `test` | 항상 | Gradle Test |
| `docker-build-verify` | test 통과 후 | JAR 빌드 → Docker Build 검증 (`push: false`) |

- ECR Push 없음
- 두 Job 중 하나라도 실패 시 Merge 불가

---

## 4. Stage CD 계약 (`cd.yml`)

### 트리거

| 이벤트 | 대상 브랜치 |
|--------|-----------|
| `push` | `develop` |

### 실행 순서

1. Gradle Test
2. `bootJar` 빌드 + layer 추출
3. GitHub OIDC → AWS IAM Role Assume
4. ECR Login
5. Docker Build + ECR Push
6. 태그: `staging-<7자리 SHA>` + `latest`
7. image digest 산출 (`docker/build-push-action` `outputs.digest`)
8. digest 출력 (infra Stage PR 연동 접점 — Native팀 협업 후 추가)

### 배포 식별자

```
<ECR_REGISTRY>/<ECR_REPOSITORY>@sha256:<DIGEST>
```

Tag보다 digest 기준으로 배포를 식별한다.

---

## 5. AWS 인증 방식 (확정)

### 제거 완료

| 항목 | 상태 |
|------|------|
| `AWS_ACCESS_KEY_ID` | **제거** |
| `AWS_SECRET_ACCESS_KEY` | **제거** |
| `AWS_SESSION_TOKEN` | **제거** |

### 적용 방식

```yaml
permissions:
  contents: read
  id-token: write

- name: Configure AWS credentials
  uses: aws-actions/configure-aws-credentials@v4
  with:
    role-to-assume: ${{ vars.AWS_ROLE_ARN }}
    aws-region: ${{ env.AWS_REGION }}
```

---

## 6. GitHub Variables (인프라팀 인계 후 등록)

| Variable | 값 | 상태 |
|----------|----|------|
| `AWS_REGION` | `ap-northeast-2` | **미등록 — 인프라 인계 대기** |
| `ECR_REPOSITORY` | `jangin-app` | **미등록 — 인프라 인계 대기** |
| `AWS_ROLE_ARN` | `<Infra 인계값>` | **미확정** |

> Workflow 파일에 AWS 값을 하드코딩하지 않는다. 모든 값은 GitHub Actions Variables로 주입한다.

---

## 7. 제거된 EC2/SSM 배포 로직

EKS 전환에 따라 아래 항목을 `cd.yml`에서 전부 제거했다.

| 제거 항목 | 비고 |
|-----------|------|
| S3 deploy script 업로드 | 불필요 |
| `EC2_INSTANCE_ID` 참조 | 불필요 |
| SSM Run Command EC2 배포 | 불필요 |
| `deploy.sh` 직접 실행 | 불필요 |
| EC2 존재 여부 검사 | 불필요 |
| 장기 AWS Access Key 인증 | OIDC로 교체 |

Dockerfile 및 Build/ECR Push 로직은 재사용한다.

---

## 8. GitOps / Argo CD 연동 규칙 (확정)

- Backend Actions는 EKS에 `kubectl apply`를 직접 수행하지 않는다.
- ECR Push 완료 후 획득한 digest를 `Jangingmall/infra` Stage overlay PR로 전달한다.
- Stage 대상 경로: `Jangingmall/infra` `main / k8s/overlays/stage`
- `jangin-app` placeholder를 실제 ECR digest로 교체하는 방식으로 연동한다.
- **infra PR 자동 생성 step은 Native팀과 접점 확정 후 `cd.yml`에 추가한다.**

---

## 9. Prod 승격 절차 (확정)

Stage에서 검증된 digest를 그대로 Prod에 승격한다.

```
Stage sha256:AAA  ←  QA 완료
      ↓
release/vX.Y.Z 생성 (Image 재빌드 없음)
      ↓
infra Prod overlay PR 생성 (sha256:AAA 기재)
      ↓ 사람 Review/Merge
Prod Argo CD Sync
      ↓
Backend Blue/Green Preview
      ↓ 사람 확인
Argo Rollouts Promote
      ↓
Prod Active
```

- Prod 승격용 infra PR은 자동 생성 가능하나, Review/Merge와 Rollout Promote는 반드시 사람의 확인을 거친다.

---

## 10. 인프라팀이 제공할 값 (미수령)

Backend팀이 직접 결정하지 않는다. 아래 값은 인프라팀 준비 후 전달 받아 GitHub Variables에 등록한다.

| 항목 | 상태 |
|------|------|
| ECR Repository URI / Repository Name | **미수령** |
| GitHub OIDC ECR Push IAM Role ARN | **미수령** |
| OIDC Trust 조건 | **미수령** |
| AWS Region | **확정: `ap-northeast-2`** |
| EKS Node ECR Pull 권한 | **미수령** |
| GitOps 대상 Repository/Path 및 PR 권한 | **미수령** |

---

## 11. Backend 완료 항목 체크리스트

| 항목 | 상태 |
|------|------|
| 기존 CI Test 흐름 유지 및 PR → develop 검증 | **완료** |
| `fix/**` 브랜치 CI 트리거 추가 | **완료** |
| Docker Build 검증 Job 추가 (PR 단계, push 없음) | **완료** |
| Stage CD Trigger를 develop Merge 기준으로 정합화 | **완료** |
| AWS 인증 Access Key → GitHub OIDC 방식 변경 | **완료** |
| ECR Push 후 image digest 산출 | **완료** |
| 기존 EC2/SSM 직접 배포 로직 제거 | **완료** |
| release에서 Image 재Build 금지 (브랜치 전략 문서화) | **완료** |
| Stage 검증 digest를 Prod 승격 대상으로 사용하는 절차 정의 | **완료** |
| AWS 값 GitHub Variables 주입 구조 적용 | **완료** |
| infra PR 자동 생성 step 추가 | **대기 — Native팀 접점 확정 후** |
| GitHub Variables 실제 값 등록 | **대기 — 인프라팀 인계 후** |

---

## 관련 문서

- [`백엔드_인프라_협의.md`](백엔드_인프라_협의.md) — EC2 아키텍처 협의 (EKS 전환 전 이력)
- [`PHASE2-4_배포_환경변수_계약서.md`](PHASE2-4_배포_환경변수_계약서.md) — 환경변수 목록
- `.github/workflows/ci.yml` — CI Workflow
- `.github/workflows/cd.yml` — Stage CD Workflow
