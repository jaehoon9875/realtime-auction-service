# infra/terraform/CLAUDE.md

프로젝트 전용 GCP 인프라 Terraform. GKE 클러스터부터 앱 리소스까지 완결.

## 관리 리소스

GKE 클러스터, VPC/서브넷, GCP Service Account(Workload Identity), Cloud SQL(PostgreSQL), Memorystore(Redis), Artifact Registry, IAM Bindings

## Terraform 상태 관리

- 상태 파일: GCS 버킷 저장, Git 커밋 금지
- terraform.tfvars: .gitignore 포함, 예시값은 terraform.tfvars.example로 관리

## 설계 원칙

- 리소스는 모듈 단위 분리
- 변경 시 반드시 terraform plan 결과 검토 후 apply. plan 없이 apply 제안 금지
- 기존 리소스 destroy/replace 시 사용자에게 명시적 경고
- GKE 노드 풀 및 VPC 변경은 운영 중단을 초래할 수 있으므로 특히 주의

## 보안 주의사항

> AI 에이전트 준수 사항

- terraform.tfvars, *.tfvars 파일이 .gitignore에 포함되어 있는지 항상 확인. 미포함 시 커밋 제안 금지
- GCP Service Account 키 JSON 파일은 이 레포지토리 어디에도 저장 금지
- terraform plan 출력의 민감한 값(DB 패스워드, SA 키 등)을 응답에 그대로 출력 금지
- outputs.tf에 민감한 값 정의 시 sensitive = true 필수
- IAM 권한은 최소 권한 원칙. roles/editor, roles/owner 같은 광범위한 역할 제안 금지
- Workload Identity 사용. SA 키 발급(key.json) 방식 제안 금지

## GKE 구성

GKE Standard, 리전: asia-northeast3(서울)

### 노드 풀 분리

| 노드 풀 | 유형 | 워크로드 |
|---------|------|----------|
| default-pool | On-demand | Kafka(Strimzi), Auction Streams, Debezium, Schema Registry, Prometheus |
| spot-pool | Spot | api-gateway, auction-service, bid-service, user-service, notification-service, Grafana |

On-demand 배치 근거:
- Kafka: PV 데이터 저장, 선점 시 파티션 리더 재선출/ISR 재구성
- Auction Streams: RocksDB State Store 로컬 유지, 선점 시 changelog 재구성으로 처리 지연
- Debezium: WAL offset 추적, 선점 시 이벤트 중복 발행 위험
- Schema Registry: 중단 시 모든 producer/consumer 스키마 조회 실패
- Prometheus: 로컬 TSDB, 선점 시 메트릭 유실

Spot 노드 풀: spot=true:NoSchedule taint. min_node_count=0으로 야간 scale-down 가능.
Notification Service는 WebSocket 연결 유지로 PodDisruptionBudget 설정 권장.
