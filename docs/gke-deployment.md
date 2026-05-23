# GKE 배포 가이드

GCP 인프라 프로비저닝부터 GKE 클러스터 배포까지의 실행 순서를 기록합니다.
전체 배포 계획 및 Phase별 설계 의도는 `temp/gke-deployment-plan.md`를 참고하세요.

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

## Phase 1. GCP API 활성화

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

## Phase 2. Terraform 상태 버킷 생성

Terraform 상태 파일을 저장할 GCS 버킷을 생성합니다. **Terraform 외부에서 수동으로 1회만 실행합니다.**

```bash
gcloud storage buckets create gs://realtime-auction-tfstate-jh9875 \
  --project=realtime-auction-service \
  --location=asia-northeast3

# 버전 관리 활성화 (상태 파일 롤백 대비)
gcloud storage buckets update gs://realtime-auction-tfstate-jh9875 \
  --versioning
```

---

## Phase 3. Terraform — GCP 인프라 프로비저닝

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
cd infra/terraform

# 1. 초기화 (버킷명 주입)
terraform init -backend-config="bucket=realtime-auction-tfstate-jh9875"

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

## Phase 4 이후

| Phase | 내용 | 상태 |
|-------|------|------|
| Phase 4 | Dockerfile 작성 (api-gateway, auction·bid·user-service, auction-streams) | 미완 |
| Phase 5 | GitHub Actions CI/CD (ci.yml, cd.yml, Workload Identity 설정) | 미완 |
| Phase 6 | K8s 매니페스트 (Kustomize base/overlays) | 미완 |
| Phase 7 | 미들웨어 설치 (Strimzi, External Secrets, ArgoCD, Prometheus) | 미완 |
| Phase 8 | ArgoCD GitOps 배포 | 미완 |

---

## 주요 리소스 정보

Terraform apply 완료 후 출력값 확인:

```bash
cd infra/terraform
terraform output
```

kubectl context 설정:

```bash
gcloud container clusters get-credentials {CLUSTER_NAME} \
  --region asia-northeast3 \
  --project realtime-auction-service
```
