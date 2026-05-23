# GKE 배포 가이드

GCP API 활성화, Terraform 상태 버킷 생성, Terraform으로 GCP 인프라를 프로비저닝하는 실행 순서입니다.

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
export TFSTATE_BUCKET="{PROJECT_ID}-tfstate"   # 예: realtime-auction-service-tfstate

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

kubectl context 설정:

```bash
gcloud container clusters get-credentials "$(terraform output -raw gke_cluster_name)" \
  --region asia-northeast3 \
  --project realtime-auction-service
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
github_org  = "{GITHUB_ORG}"
github_repo = "{GITHUB_REPO}"
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
gh secret set GCP_PROJECT_ID                  --body "{PROJECT_ID}"
gh secret set GCP_WORKLOAD_IDENTITY_PROVIDER  --body "$(terraform output -raw workload_identity_provider)"
gh secret set GCP_SERVICE_ACCOUNT             --body "$(terraform output -raw github_actions_sa_email)"
```

등록 확인:

```bash
gh secret list
```

### 워크플로 동작

| 워크플로 | 트리거 | 동작 |
|----------|--------|------|
| `ci.yml` | PR·main push | Gradle test → Docker build (PR) / Artifact Registry push (main) |
| `cd.yml` | main CI 성공 후 | `infra/k8s/overlays/dev` 이미지 태그 갱신 → Git push |

main push 시 이미지 태그는 커밋 SHA 앞 7자(`SHORT_SHA`)와 `latest` 두 개가 푸시됩니다.
CD는 `SHORT_SHA`로 Kustomize overlay를 갱신하며, ArgoCD 자동 sync는 Phase 7에서 설정합니다.

---

## 6. Branch Protection Rules 설정

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
