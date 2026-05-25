# infra/k8s/CLAUDE.md

앱 레벨 Kubernetes 매니페스트. Kustomize base/overlays 구조, ArgoCD GitOps 배포.

## 설정값 관리

- 환경변수는 ConfigMap, 민감 정보는 Secret으로 분리
- Secret 실제 값은 이 레포에 커밋하지 않음. GCP Secret Manager 또는 External Secrets Operator 사용
- configmap.yaml에 placeholder만, 실제 값은 Secret 참조로 처리

## 배포 규칙

- 롤백: Git revert → ArgoCD sync
- deployment.yaml에 resources.requests/limits 반드시 명시
- 모든 서비스에 Liveness/Readiness probe 설정
- 앱 리소스 네임스페이스는 `auction` 고정
- 리소스명은 `{service-name}-{resource-type}` 형식 사용 (예: `auction-service-deployment`)

## ArgoCD Sync Wave 순서

리소스 간 기동 의존성을 sync-wave 어노테이션으로 명시. ArgoCD는 각 wave가 Healthy 상태가 된 후 다음 wave를 진행한다.

| Wave | 리소스 | 파일 |
|------|--------|------|
| 1 | Kafka (Strimzi CR) | kafka/kafka-cluster.yaml |
| 2 | Debezium Deployment | debezium/deployment.yaml |
| (PostSync) | connector-register-job | debezium/connector-register-job.yaml |

Debezium readinessProbe가 `/connectors`를 체크하므로 Wave 2 Healthy = REST API 응답 가능.
PostSync Job은 이 순서가 보장된 후 실행되어 별도 대기 루프 없이 커넥터를 즉시 등록할 수 있다.

## Strimzi Kafka

GKE 클러스터 Kafka는 Strimzi Operator 관리. Operator 자체는 cloud-sre-platform에서 설치.
이 레포에서는 KafkaTopic CR만 정의.
- KafkaTopic CR의 apiVersion은 `kafka.strimzi.io/v1` 사용 (Strimzi Operator 1.0.0+, v1beta2 제거됨)
