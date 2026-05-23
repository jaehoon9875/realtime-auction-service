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
