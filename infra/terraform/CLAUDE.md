# infra/terraform/CLAUDE.md

이 프로젝트 전용 GCP 인프라를 관리하는 Terraform. GKE 클러스터부터 앱 리소스까지 이 레포 안에서 완결된다.

---

## 관리 범위

| 리소스 | 목적 |
|--------|------|
| GKE 클러스터 | 서비스 배포 환경 |
| VPC / 서브넷 | 클러스터 네트워크 |
| GCP Service Account | 각 서비스의 Workload Identity 바인딩 |
| Cloud SQL (PostgreSQL) | auction, bid, user 서비스용 DB 인스턴스 |
| Memorystore (Redis) | notification-service WebSocket 세션 공유 |
| Artifact Registry | 서비스 Docker 이미지 저장소 |
| IAM Bindings | Service Account → GCP 리소스 접근 권한 |

---

## 디렉토리 구조

```
terraform/
├── main.tf               # provider, backend 설정
├── variables.tf
├── outputs.tf
├── modules/
│   ├── gke/              # GKE 클러스터 + 노드 풀
│   ├── vpc/              # VPC, 서브넷
│   ├── cloud-sql/        # PostgreSQL 인스턴스
│   ├── memorystore/      # Redis 인스턴스
│   └── workload-identity/ # Service Account + IAM 바인딩
└── envs/
    ├── dev/              # 개발 환경 tfvars
    └── prod/             # 프로덕션 환경 tfvars
```

---

## Terraform 상태 관리

- 상태 파일은 GCS 버킷에 저장한다 (`{project-id}-tfstate`).
- 상태 파일을 Git에 커밋하지 않는다 (`.gitignore`에 포함).
- `terraform.tfvars`는 `.gitignore`에 포함. 예시값은 `terraform.tfvars.example`로 관리.

---

## 워크플로

```bash
cd infra/terraform/envs/dev
terraform init
terraform plan
terraform apply
```

CI/CD에서는 PR 시 `terraform plan` 결과를 PR 코멘트로 게시한다.
`terraform apply`는 main 브랜치 머지 후 수동 또는 자동으로 실행한다.
