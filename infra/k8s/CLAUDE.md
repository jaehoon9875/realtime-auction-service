# infra/k8s/CLAUDE.md

앱 레벨 Kubernetes 매니페스트. Kustomize base/overlays 구조, ArgoCD GitOps 배포.

## 설정값 관리

- 환경변수는 ConfigMap, 민감 정보는 Secret으로 분리
- Secret 실제 값은 이 레포에 커밋하지 않음. GCP Secret Manager 또는 External Secrets Operator 사용
- configmap.yaml에 placeholder만, 실제 값은 Secret 참조로 처리

## 배포 규칙

- kubectl apply / helm upgrade 직접 실행 금지. 파일 수정 → Git push → ArgoCD sync만 허용
- 롤백: Git revert → ArgoCD sync
- deployment.yaml에 resources.requests/limits 반드시 명시
- 모든 서비스에 Liveness/Readiness probe 설정
- 앱 리소스 네임스페이스는 `auction` 고정
- 리소스명은 `{service-name}-{resource-type}` 형식 사용 (예: `auction-service-deployment`)

## Strimzi Kafka

GKE 클러스터 Kafka는 Strimzi Operator 관리. Operator 자체는 cloud-sre-platform에서 설치.
이 레포에서는 KafkaTopic CR만 정의.
- KafkaTopic CR의 apiVersion은 `kafka.strimzi.io/v1beta2` 사용
