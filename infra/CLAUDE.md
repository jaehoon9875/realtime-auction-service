# infra/CLAUDE.md

인프라 구성 파일 디렉토리. 주요 구성: docker-compose.yml(로컬), k8s/(Kubernetes 매니페스트), terraform/(GCP 리소스).

## 배포 방식

하이브리드 방식. 변경 빈도와 역할에 따라 분리.

| 구분 | 관리 방식 | 대상 |
|------|-----------|------|
| 인프라 | ArgoCD GitOps | Kafka, Zookeeper, PostgreSQL, Redis, ESO 등 |
| Java 서비스 | GitHub Actions (kubectl set image) | api-gateway, auction, bid, user, notification, auction-streams |

인프라: infra/ 파일 수정 → Git push → ArgoCD sync. kubectl apply 직접 수정 금지.
Java 서비스: CI 통과 후 CD 파이프라인(cd.yml)이 kubectl set image로 직접 배포.
관련 이슈: https://github.com/jaehoon9875/realtime-auction-service/issues/41
