# GKE 배포 가이드

GCP API 활성화부터 Terraform 인프라 프로비저닝, Secret Manager·GitHub 설정, Helm 미들웨어 설치까지의 실행 순서입니다.

> **범위:** 이 문서는 9절(Helm 미들웨어)까지입니다. 앱 매니페스트 ArgoCD Application 등록·sync는 별도 작업입니다.

---

## 사전 준비

### 로컬 툴 확인

```bash
terraform version   # >= 1.6
kubectl version --client
helm version
gcloud version
```

### GCP 인증

```bash
gcloud auth application-default login
gcloud config set project realtime-auction-service
```

---

## 1. GCP API 활성화

```bash
gcloud services enable \
  compute.googleapis.com \
  container.googleapis.com \
  sqladmin.googleapis.com \
  redis.googleapis.com \
  artifactregistry.googleapis.com \
  secretmanager.googleapis.com \
  servicenetworking.googleapis.com \
  iam.googleapis.com \
  --project=realtime-auction-service
```

활성화 후 propagation에 1~2분 소요될 수 있습니다.

---

## 2. Terraform 상태 버킷 생성

Terraform 상태 파일을 저장할 GCS 버킷을 생성합니다. **Terraform 외부에서 수동으로 1회만 실행합니다.**

버킷명은 GCS 전역에서 유일해야 합니다. 아래 `{TFSTATE_BUCKET}`을 본인 환경에 맞게 정한 뒤, 이후 `terraform init`의 `bucket` 값과 동일하게 맞춥니다.

```bash
export TFSTATE_BUCKET="realtime-auction-service-tfstate"

gcloud storage buckets create gs://${TFSTATE_BUCKET} \
  --project=realtime-auction-service \
  --location=asia-northeast3

# 버전 관리 활성화 (상태 파일 롤백 대비)
gcloud storage buckets update gs://${TFSTATE_BUCKET} \
  --versioning
```

---

## 3. Terraform — GCP 인프라 프로비저닝

### terraform.tfvars 설정

`infra/terraform/terraform.tfvars.example`을 복사해 `terraform.tfvars`를 작성합니다.
(`terraform.tfvars`는 `.gitignore`에 포함되어 있습니다.)

```bash
cp infra/terraform/terraform.tfvars.example infra/terraform/terraform.tfvars
```

| 변수 | 설명 |
|------|------|
| `project_id` | GCP 프로젝트 ID |
| `region` / `zone` | `asia-northeast3` / `asia-northeast3-a` (서울, 변경 불필요) |
| `app_db_password` | Cloud SQL `app` 사용자 패스워드 (auction·bid·user DB 공통) |
| `debezium_db_password` | Cloud SQL `debezium` 사용자 패스워드 |
| `github_org` | GitHub 사용자명 (Workload Identity 바인딩용) |
| `github_repo` | GitHub 레포지토리명 |

패스워드 생성 예시:

```bash
openssl rand -base64 24
```

### 실행

```bash
# 2단계와 동일한 버킷명 (미설정 시 export TFSTATE_BUCKET=... 선행)
cd infra/terraform

# 1. 초기화
terraform init -backend-config="bucket=${TFSTATE_BUCKET}"

# 2. 플랜 검토 (필수)
terraform plan -out=tfplan

# 3. 적용
terraform apply tfplan
```

> `terraform apply`는 반드시 `terraform plan` 결과를 검토한 후 실행합니다.

### 생성되는 리소스

| 모듈 | 리소스 |
|------|--------|
| `vpc` | VPC, 서브넷, Cloud NAT, Private Service Access |
| `gke` | GKE Standard 클러스터, default-pool(On-demand), spot-pool(Spot) |
| `cloud-sql` | PostgreSQL 17 × 3 (auction·bid·user DB), Private IP |
| `memorystore` | Redis 7, Private IP |
| `artifact-registry` | Docker 이미지 저장소 |
| `iam` | Workload Identity Pool, GitHub Actions SA, External Secrets SA |

---

## 4. apply 후 확인

Terraform 출력값 확인:

```bash
cd infra/terraform
terraform output
```

kubectl context 설정 (GKE는 Zonal 클러스터 — `terraform.tfvars`의 `zone`과 동일):

```bash
gcloud container clusters get-credentials "$(terraform output -raw gke_cluster_name)" \
  --zone asia-northeast3-a \
  --project realtime-auction-service

kubectl config current-context   # ...asia-northeast3-a_auction-cluster 확인
kubectl get nodes
```

GitHub Actions·External Secrets 연동에 필요한 값은 `artifact_registry_url`, `github_actions_sa_email`, `workload_identity_provider` 출력을 참고합니다.

### Workload Identity Federation 확인

`iam` 모듈이 다음 리소스를 자동으로 생성합니다.

| 리소스 | 설명 |
|--------|------|
| Workload Identity Pool | GitHub OIDC 토큰을 GCP 자격증명으로 변환하는 풀 |
| OIDC Provider | `token.actions.githubusercontent.com` 발급자 등록 |
| GitHub Actions SA | Artifact Registry 푸시·GKE 접근 권한을 가진 서비스 계정 |
| SA 바인딩 | `terraform.tfvars`의 `github_org`/`github_repo` 값으로 해당 레포만 SA 가장 허용 |

`terraform.tfvars`에서 아래 두 변수가 올바르게 설정되어야 WIF 바인딩이 정상 생성됩니다.

```hcl
github_org  = "jaehoon9875"
github_repo = "realtime-auction-service"
```

apply 후 생성 확인:

```bash
cd infra/terraform
terraform output workload_identity_provider   # projects/{NUMBER}/locations/global/workloadIdentityPools/...
terraform output github_actions_sa_email      # github-actions@....iam.gserviceaccount.com
```

WIF Pool, OIDC Provider, SA 생성 및 바인딩은 `infra/terraform/modules/iam/main.tf`에 정의되어 있으며 `terraform apply` 시 자동으로 프로비저닝됩니다.

---

## 5. GitHub Actions — Secrets 등록

Terraform apply 완료 후 아래 명령어로 GitHub Secrets를 등록합니다. (`gh` CLI 필요)

```bash
cd infra/terraform
gh secret set GCP_PROJECT_ID                  --body "realtime-auction-service"
gh secret set GCP_WORKLOAD_IDENTITY_PROVIDER  --body "$(terraform output -raw workload_identity_provider)"
gh secret set GCP_SERVICE_ACCOUNT             --body "$(terraform output -raw github_actions_sa_email)"
```

등록 확인:

```bash
gh secret list -R jaehoon9875/realtime-auction-service
```

### 워크플로 동작

| 워크플로 | 트리거 | 동작 |
|----------|--------|------|
| `ci.yml` | PR·main push | Gradle test → Docker build (PR) / Artifact Registry push (main) |
| `cd.yml` | main CI 성공 후 | `infra/k8s/overlays/dev` 이미지 태그 갱신 → Git push |

main push 시 이미지 태그는 커밋 SHA 앞 7자(`SHORT_SHA`)와 `latest` 두 개가 푸시됩니다.
CD는 `SHORT_SHA`로 Kustomize overlay를 갱신하며, ArgoCD 자동 sync는 Phase 7에서 설정합니다.

---

## 6. GitHub 설정

### 6-1. Actions Workflow permissions

`cd.yml`이 main에 이미지 태그 커밋을 push하려면 write 권한이 필요합니다.

**Settings → Actions → General → Workflow permissions** → **Read and write permissions** 선택

(기본값 read-only이면 CD push가 거부됩니다.)

### 6-2. Branch Protection Rules

GitHub 레포지토리 **Settings → Branches → Add branch ruleset** (또는 classic protection rules)에서 `main` 브랜치에 아래 규칙을 적용합니다.

### 설정 항목

| 항목 | 값 |
|------|----|
| Require a pull request before merging | ✅ 활성화 |
| Required approvals | 1 |
| Required status checks | `ci-success` |
| Do not allow bypassing the above settings | ❌ 비활성화 (Actions 봇 push 허용 필요) |

### Required Status Check 등록

`ci-success`는 `ci.yml`의 집계 job 이름입니다. PR을 최소 1번 열어 CI가 실행된 뒤에야 GitHub UI에서 선택 가능합니다.

### GitHub Actions 봇 bypass 허용

`cd.yml`은 이미지 태그 업데이트 커밋을 `main`에 직접 push합니다. Branch Protection이 이를 차단하지 않도록 bypass actor를 추가합니다.

**Ruleset 사용 시**: Bypass list → Add bypass → `github-actions[bot]` (Role: Repository role)

**Classic Protection 사용 시**: Allow specified actors to bypass required pull requests → `github-actions[bot]` 추가

> CD 커밋에는 `[skip ci]`가 포함되어 있어 CI가 재실행되지 않습니다.

---

## 7. GCP Secret Manager — 시크릿 등록

K8s 매니페스트를 배포하기 전 GCP Secret Manager에 아래 시크릿을 등록해야 합니다.
External Secrets Operator가 이 값들을 읽어 K8s Secret을 자동으로 생성합니다.

> Terraform apply가 완료된 후 실행합니다. DB 패스워드는 `terraform.tfvars`에 설정한 값과 동일하게 입력합니다.

### 등록 명령어

아래 `--project`는 `terraform.tfvars`의 `project_id`와 동일하게 맞춥니다.

```bash
PROJECT_ID=realtime-auction-service

# ── 값 생성 (JWT·내부 시크릿) ──────────────────────────────────
INTERNAL_SECRET=$(openssl rand -base64 32)
openssl genrsa -out jwt-private.pem 2048
openssl rsa -in jwt-private.pem -pubout -out jwt-public.pem
JWT_PRIVATE_KEY=$(cat jwt-private.pem)
JWT_PUBLIC_KEY=$(cat jwt-public.pem)

# ── 패스워드 입력 (터미널 히스토리에 남지 않도록 read 사용) ─────
read -s APP_DB_PASSWORD       # terraform.tfvars의 app_db_password 와 동일
read -s DEBEZIUM_DB_PASSWORD  # terraform.tfvars의 debezium_db_password 와 동일
# Terraform Memorystore 모듈은 AUTH 미설정 → 빈 값(Enter)으로 등록
read -s REDIS_PASSWORD && echo

# ── DB 패스워드 ────────────────────────────────────────────────
echo -n "$APP_DB_PASSWORD"      | gcloud secrets create auction-db-password         --project=$PROJECT_ID --data-file=-
echo -n "$APP_DB_PASSWORD"      | gcloud secrets create bid-db-password              --project=$PROJECT_ID --data-file=-
echo -n "$APP_DB_PASSWORD"      | gcloud secrets create user-db-password             --project=$PROJECT_ID --data-file=-
echo -n "$DEBEZIUM_DB_PASSWORD" | gcloud secrets create debezium-postgres-password   --project=$PROJECT_ID --data-file=-
echo -n "debezium"              | gcloud secrets create debezium-postgres-user       --project=$PROJECT_ID --data-file=-

# ── Redis ──────────────────────────────────────────────────────
REDIS_HOST=$(cd infra/terraform && terraform output -raw redis_host)
echo -n "$REDIS_HOST"     | gcloud secrets create redis-host     --project=$PROJECT_ID --data-file=-
echo -n "$REDIS_PASSWORD" | gcloud secrets create redis-password --project=$PROJECT_ID --data-file=-

# ── 서비스 간 내부 인증 토큰 ────────────────────────────────────
echo -n "$INTERNAL_SECRET" | gcloud secrets create internal-request-secret --project=$PROJECT_ID --data-file=-

# ── JWT 키 페어 ────────────────────────────────────────────────
echo -n "$JWT_PRIVATE_KEY" | gcloud secrets create jwt-private-key --project=$PROJECT_ID --data-file=-
echo -n "$JWT_PUBLIC_KEY"  | gcloud secrets create jwt-public-key  --project=$PROJECT_ID --data-file=-

# JWT PEM 파일은 등록 후 삭제 권장
rm -f jwt-private.pem jwt-public.pem
```

> JWT 키는 `read -s`로 입력하면 PEM 줄바꿈이 깨집니다. 위처럼 파일 생성 후 `cat`으로 변수에 담아 등록하세요.

### 등록 확인

```bash
gcloud secrets list --project=realtime-auction-service
# 10개 시크릿(auction/bid/user/debezium×2, redis×2, internal, jwt×2) 확인
```

### 전체 시크릿 목록

| Secret 이름 | 용도 | 사용하는 서비스 |
|---|---|---|
| `auction-db-password` | Cloud SQL 패스워드 | auction-service |
| `bid-db-password` | Cloud SQL 패스워드 | bid-service |
| `user-db-password` | Cloud SQL 패스워드 | user-service |
| `debezium-postgres-user` | CDC 전용 DB 계정명 | debezium |
| `debezium-postgres-password` | CDC 전용 DB 패스워드 | debezium |
| `redis-host` | Memorystore private IP | user-service, notification-service |
| `redis-password` | Memorystore 패스워드 | user-service, notification-service |
| `internal-request-secret` | 서비스 간 내부 토큰 | api-gateway, auction-service, bid-service |
| `jwt-private-key` | JWT 서명 키 | user-service |
| `jwt-public-key` | JWT 검증 키 | api-gateway, user-service |

---

## 8. K8s 매니페스트 배포 — 사전 설정

GCP 프로젝트 ID(`realtime-auction-service`)는 Cloud SQL 연결명·Workload Identity·Artifact Registry 경로 등에 **매니페스트에 하드코딩**되어 있습니다 (`infra/k8s/`). ArgoCD sync 전 별도 치환은 필요 없습니다.

### Cloud SQL Auth Proxy 동작 방식

이 프로젝트는 Cloud SQL에 IP 직접 접속 대신 **Auth Proxy 사이드카 패턴**을 사용합니다.

```
Pod 내부
┌───────────────────────────────────────┐
│  app (auction-service 등)             │
│    → localhost:5432 접속              │
│                                       │
│  cloud-sql-proxy (사이드카)            │
│    ← GKE Workload Identity 로 IAM 인증 │
│    → Cloud SQL 에 암호화 터널링        │
└───────────────────────────────────────┘
```

**장점:**
- DB 비밀번호 없이 IAM으로 인증 (Workload Identity)
- DB private IP를 코드·설정 파일에 기재하지 않아도 됨
- SSL/TLS 자동 처리

**Debezium은 두 DB에 접속하므로 사이드카 2개:**
- `cloud-sql-proxy-auction` → `localhost:5432` (auction-db)
- `cloud-sql-proxy-bid` → `localhost:5433` (bid-db)

Auth Proxy 사이드카는 각 서비스 Deployment에 이미 정의되어 있습니다 (`infra/k8s/base/{service}/deployment.yaml`).

---

## 9. 미들웨어 설치 — Helm

GKE 클러스터에 kubectl context가 설정된 상태에서 **프로젝트 루트**에서 순서대로 실행합니다 (4절 참고).
각 설치는 1회성 작업입니다.

### 9-0. 앱 네임스페이스 생성

Strimzi Operator는 `watchNamespaces`에 지정한 namespace가 **미리 존재**해야 설치됩니다.

```bash
kubectl create namespace auction
```

### 9-1. Strimzi Operator (Kafka)

```bash
helm repo add strimzi https://strimzi.io/charts/
helm repo update

helm install strimzi-kafka-operator strimzi/strimzi-kafka-operator \
  --namespace strimzi-system \
  --create-namespace \
  --set watchNamespaces="{auction}"
```

설치 확인:

```bash
kubectl -n strimzi-system rollout status deployment/strimzi-cluster-operator
```

### 9-2. External Secrets Operator

```bash
helm repo add external-secrets https://charts.external-secrets.io
helm repo update

helm install external-secrets external-secrets/external-secrets \
  --namespace external-secrets-system \
  --create-namespace
```

설치 확인:

```bash
kubectl -n external-secrets-system rollout status deployment/external-secrets
```

### 9-3. ArgoCD

```bash
helm repo add argo https://argoproj.github.io/argo-helm
helm repo update

kubectl create namespace argocd

# 프로젝트 루트에서 실행 (values.yaml 경로)
helm install argocd argo/argo-cd \
  --namespace argocd \
  -f infra/argocd/values.yaml
```

초기 admin 패스워드 확인:

```bash
kubectl -n argocd get secret argocd-initial-admin-secret \
  -o jsonpath="{.data.password}" | base64 -d && echo
```

ArgoCD UI 접근 (LoadBalancer IP 확인):

```bash
kubectl -n argocd get svc argocd-server
```

### 9-4. kube-prometheus-stack (Prometheus + Grafana)

```bash
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo update

helm install kube-prometheus-stack prometheus-community/kube-prometheus-stack \
  --namespace monitoring \
  --create-namespace
```

Grafana 초기 패스워드 확인:

```bash
kubectl -n monitoring get secret kube-prometheus-stack-grafana \
  -o jsonpath="{.data.admin-password}" | base64 -d && echo
```

---

## 다음 단계

이 문서까지 완료하면 GKE 인프라·시크릿·미들웨어 준비가 끝납니다. 이후:

1. ArgoCD Application 등록 → `infra/k8s/overlays/dev` GitOps sync
2. main merge 후 CI/CD로 Artifact Registry 이미지 push·태그 갱신 확인
3. Pod 기동·Debezium Connector 등록 등 앱 레벨 검증
