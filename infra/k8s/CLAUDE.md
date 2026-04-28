# infra/k8s/CLAUDE.md

이 프로젝트의 앱 레벨 Kubernetes 매니페스트. ArgoCD로 GKE 클러스터에 GitOps 방식으로 배포된다.

---

## 디렉토리 구조

```
k8s/
├── base/                        # Kustomize base
│   ├── auction-service/
│   │   ├── deployment.yaml
│   │   ├── service.yaml
│   │   └── configmap.yaml
│   ├── bid-service/
│   ├── user-service/
│   ├── notification-service/
│   ├── auction-streams/
│   └── api-gateway/
├── overlays/
│   ├── dev/                     # 개발 환경 오버레이
│   └── prod/                    # 프로덕션 환경 오버레이
└── argocd/
    └── application.yaml         # ArgoCD Application 정의
```

---

## 네이밍 컨벤션

- 리소스명: `{service-name}-{resource-type}` (예: `auction-service-deployment`)
- 네임스페이스: `auction` (모든 앱 리소스는 이 네임스페이스에 배포)
- 레이블: `app.kubernetes.io/name`, `app.kubernetes.io/component` 필수

---

## 설정값 관리

- 환경변수는 `ConfigMap`으로, 민감 정보는 `Secret`으로 분리한다.
- Secret의 실제 값은 **이 레포에 커밋하지 않는다.** GCP Secret Manager 또는 External Secrets Operator를 사용한다.
- `configmap.yaml`에 placeholder만 두고 실제 값은 Secret 참조로 처리한다.

---

## 배포 규칙

- **직접 `kubectl apply` 또는 `helm upgrade` 금지.** 파일 수정 → Git push → ArgoCD sync만 허용.
- 롤백도 Git revert → ArgoCD sync로 처리한다.
- `deployment.yaml`에는 반드시 `resources.requests/limits`를 명시한다.
- Liveness/Readiness probe는 모든 서비스에 설정한다.

---

## Strimzi Kafka

GKE 클러스터 위의 Kafka는 Strimzi Operator로 관리된다.
Strimzi Operator 자체는 `cloud-sre-platform`에서 설치되며, 이 레포에서는 `KafkaTopic` CR만 정의한다.

```yaml
# 예시: KafkaTopic CR
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

## 관련 문서

- [infra/CLAUDE.md](../CLAUDE.md) — 인프라 전체 구조 및 역할 분리
- [infra/terraform/CLAUDE.md](../terraform/CLAUDE.md) — GCP 앱 레벨 리소스
