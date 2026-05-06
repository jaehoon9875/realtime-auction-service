# infra/CLAUDE.md

인프라 구성 파일 디렉토리. 주요 구성: docker-compose.yml(로컬), k8s/(Kubernetes 매니페스트), terraform/(GCP 리소스).

## GitOps 원칙

- 인프라 변경은 Git을 통해서만 반영한다 (ArgoCD). kubectl apply / helm upgrade CLI로 직접 수정 금지.
- 변경 경로: infra/ 파일 수정 → Git push → ArgoCD sync. 핫픽스 포함 예외 없음.
