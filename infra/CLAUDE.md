# infra/CLAUDE.md

이 프로젝트의 인프라 구성 파일 디렉토리.

---

## 디렉토리 구조

```
infra/
├── docker-compose.yml    # 로컬 개발 환경 (Kafka, PostgreSQL, Redis, Debezium, Schema Registry)
├── k8s/                  # Kubernetes 매니페스트
└── terraform/            # GCP 리소스 (GKE 클러스터 포함)
```

---

## GitOps 원칙

- ArgoCD로 관리되는 리소스는 `kubectl apply`나 `helm upgrade`로 직접 수정하지 않는다.
- 변경은 반드시 `infra/` 파일 수정 → Git push → ArgoCD sync 경로로만 반영한다.
- 핫픽스도 예외 없음. 긴급 시에도 PR → ArgoCD sync 방식을 유지한다.

---

## 로컬 개발

```bash
# 전체 로컬 인프라 실행
docker-compose up -d

# 특정 서비스만
docker-compose up -d kafka postgres-auction redis
```

상세 내용은 [docker-compose.yml](docker-compose.yml) 참고.
