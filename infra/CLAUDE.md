# infra/CLAUDE.md

인프라 구성 파일 디렉토리. 주요 구성: docker-compose.yml(로컬), k8s/(Kubernetes 매니페스트), terraform/(GCP 리소스).

## GitOps 원칙

- ArgoCD 관리 리소스는 kubectl/helm CLI로 직접 수정 금지
- 변경 경로: infra/ 파일 수정 → Git push → ArgoCD sync
- 핫픽스 포함 예외 없음
