# infra/CLAUDE.md

인프라 구성 파일 디렉토리. 주요 구성: docker-compose.yml(로컬), k8s/(Kubernetes 매니페스트), terraform/(GCP 리소스).

## 배포 방식

하이브리드 방식. 변경 빈도와 역할에 따라 분리.

| 구분 | 관리 방식 | 대상 |
|------|-----------|------|
| 인프라 | ArgoCD GitOps | Kafka, Zookeeper, PostgreSQL, Redis, ESO 등 |
| Java 서비스 | ArgoCD GitOps (auto-sync) | api-gateway, auction, bid, user, notification, auction-streams |

인프라: infra/ 파일 수정 → Git push → ArgoCD auto-sync.
Java 서비스: CI 통과 후 CD 파이프라인(cd.yml)이 kustomization.yaml 이미지 태그를 커밋 → ArgoCD가 Git 변경 감지 후 자동 배포.
CD 파이프라인은 이미지 태그 커밋 SHA를 기준으로 의존 Application의 Synced + Healthy 상태를 확인한 후 smoke test를 실행.
ArgoCD webhook(GitHub → ArgoCD): Git push 즉시 sync 트리거. 관련 설정: infra/argocd/webhook-secret.yaml, docs/gke-deployment.md 9-3-2절.
관련 이슈: https://github.com/jaehoon9875/realtime-auction-service/issues/41
