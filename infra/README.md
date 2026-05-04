# infra

인프라 구성 파일 디렉토리입니다. 환경별 상세 운영 가이드는 `docs/`를 참고하세요.

---

## 디렉토리 구조

```text
infra/
├── docker-compose.yml    # 로컬 개발 환경
├── .env.example          # 환경변수 템플릿
├── avro/                 # Avro 스키마 원본 + Schema Registry 등록 스크립트
├── debezium/             # Kafka Connect + Debezium 커스텀 이미지
├── init-scripts/         # PostgreSQL 초기화 스크립트
├── k8s/                  # Kubernetes 매니페스트
└── terraform/            # GCP 리소스 (GKE 클러스터)
```

---

## 환경별 가이드

| 환경 | 도구 | 문서 |
|------|------|------|
| 로컬 개발 | docker-compose | [docs/local-dev.md](../docs/local-dev.md) |
| Debezium Connector 등록 | Kafka Connect REST API | [docs/debezium-connector.md](../docs/debezium-connector.md) |
| Avro / Schema Registry | Confluent Schema Registry REST API | [docs/avro-schema.md](../docs/avro-schema.md) |
| 운영 배포 | GKE + ArgoCD | [infra/k8s/](./k8s/) |
| 인프라 프로비저닝 | Terraform | [infra/terraform/](./terraform/) |

---

## k8s 구조

Kustomize base/overlays 구조. ArgoCD GitOps로 GKE 클러스터에 배포됩니다.

```text
k8s/
├── base/
│   ├── auction-service/       # deployment.yaml, service.yaml, configmap.yaml
│   ├── bid-service/
│   ├── user-service/
│   ├── notification-service/
│   ├── auction-streams/
│   └── api-gateway/
├── overlays/
│   ├── dev/
│   └── prod/
└── argocd/
    └── application.yaml
```

### 네이밍 컨벤션

- 리소스명: `{service-name}-{resource-type}` (예: `auction-service-deployment`)
- 네임스페이스: `auction`
- 레이블: `app.kubernetes.io/name`, `app.kubernetes.io/component` 필수

### KafkaTopic CR 예시 (Strimzi)

```yaml
apiVersion: kafka.strimzi.io/v1beta2
kind: KafkaTopic
metadata:
  name: bid-events
  namespace: auction
spec:
  partitions: 6
  replicas: 3
```

---

## terraform 구조

프로젝트 전용 GCP 인프라 (GKE, Cloud SQL, Redis, VPC 등).

```text
terraform/
├── main.tf / variables.tf / outputs.tf
├── modules/
│   ├── gke/
│   ├── vpc/
│   ├── cloud-sql/
│   ├── memorystore/
│   └── workload-identity/
└── envs/
    ├── dev/
    └── prod/
```

### 워크플로

```bash
cd infra/terraform/envs/dev
terraform init
terraform plan
terraform apply
```

CI/CD: PR 시 `terraform plan` 결과를 PR 코멘트로 게시. `terraform apply`는 main 머지 후 실행.

### Spot 노드 풀 toleration/nodeAffinity 예시

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
