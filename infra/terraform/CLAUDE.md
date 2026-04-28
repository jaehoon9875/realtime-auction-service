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

---

## 설계 원칙

- 리소스는 모듈 단위로 분리하여 재사용 가능하게 구성한다.
- Terraform state는 GCS backend에 저장하고 Git에는 절대 커밋하지 않는다.
- 민감한 값(credentials, password 등)은 `tfvars`에만 존재하며 `.gitignore` 처리한다.
- 리소스 변경 시 반드시 `terraform plan` 결과를 검토한 후 `apply`한다. **plan 없이 apply를 제안하지 않는다.**
- 기존 리소스를 destroy하거나 교체(replace)하는 변경은 반드시 사용자에게 명시적으로 경고한다.

---

## 보안 주의사항

> **AI 에이전트는 아래 사항을 반드시 준수한다.**

- `terraform.tfvars`, `*.tfvars` 파일은 `.gitignore`에 포함되어 있는지 항상 확인한다. 미포함 시 커밋을 제안하지 않는다.
- GCP Service Account 키 JSON 파일은 이 디렉토리 또는 리포지토리 어디에도 저장하지 않는다.
- `terraform plan` 출력에는 민감한 값(DB 패스워드, SA 키 등)이 포함될 수 있으므로, 해당 내용을 외부에 공유하거나 응답에 그대로 출력하지 않는다.
- `outputs.tf`에 민감한 값을 정의할 경우 반드시 `sensitive = true`를 명시한다.
- IAM 권한은 최소 권한 원칙(Principle of Least Privilege)을 따른다. 편의를 위해 `roles/editor`나 `roles/owner` 같은 광범위한 역할을 제안하지 않는다.
- Workload Identity를 사용하며, SA 키를 발급(key.json)하는 방식은 제안하지 않는다.

---

## GKE 구성 방향

- GKE Standard 클러스터 (Autopilot 아님)
- 리전: `asia-northeast3` (서울)
- 노드 풀은 워크로드 특성에 따라 분리한다.

### 노드 풀 분리 계획

| 노드 풀 | 유형 | 워크로드 | 이유 |
|---------|------|----------|------|
| `default-pool` | On-demand | Kafka (Strimzi), Auction Streams, Debezium, Schema Registry, Prometheus | stateful 또는 선점 시 복구 비용이 큰 워크로드 |
| `spot-pool` | Spot(preemptible) | api-gateway, auction-service, bid-service, user-service, notification-service, Grafana | stateless 서비스, 선점되어도 빠르게 재스케줄 가능 |

### 워크로드별 On-demand 배치 근거

- **Kafka (Strimzi)**: 파티션 데이터를 PV에 저장. 선점 시 파티션 리더 재선출 및 ISR 재구성 발생.
- **Auction Streams**: Kafka Streams의 RocksDB State Store(현재 최고가)를 로컬에 유지. 선점 시 changelog 토픽에서 state를 전체 재구성해야 하므로 처리 지연이 발생한다.
- **Debezium**: WAL offset을 추적하여 CDC를 수행. 선점 시 offset 재처리로 이벤트 중복 발행 위험이 있다.
- **Schema Registry**: Avro 스키마 저장소. 중단 시 모든 producer/consumer의 스키마 조회 실패.
- **Prometheus**: 로컬 TSDB에 메트릭 저장. 선점 시 수집 데이터 유실.

### Spot 노드 풀 taint 전략

- Spot 노드 풀에는 `spot=true:NoSchedule` taint를 설정하여 toleration 없는 Pod가 올라오지 못하게 한다.
- stateless 서비스 Pod의 K8s 매니페스트에는 아래 toleration과 nodeAffinity를 명시한다:

```yaml
tolerations:
  - key: "spot"
    operator: "Equal"
    value: "true"
    effect: "NoSchedule"
affinity:
  nodeAffinity:
    preferredDuringSchedulingIgnoredDuringExecution:
      - weight: 1
        preference:
          matchExpressions:
            - key: cloud.google.com/gke-spot
              operator: In
              values: ["true"]
```

- Spot 노드 풀은 `min_node_count = 0` 설정으로 야간 scale-down 가능하다.
- **Notification Service**는 stateless이나 WebSocket 연결을 유지하므로, 선점 시 클라이언트 재연결이 발생한다. Redis 세션 공유로 서비스 연속성은 보장되지만 UX 영향을 감안해 `PodDisruptionBudget` 설정을 권장한다.
