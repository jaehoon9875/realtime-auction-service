# docs

프로젝트 설계 문서, 의사결정 기록, API 명세를 관리합니다.

이슈 추적은 [GitHub Issues](https://github.com/jaehoon9875/realtime-auction-service/issues)에서 관리합니다.

---

## 환경 구축 시작점

**로컬 개발**: [local-dev.md](local-dev.md) → [debezium-connector.md](debezium-connector.md) → [avro-schema.md](avro-schema.md)

**GKE 배포**: [gke-deployment.md](gke-deployment.md) → [sealed-secrets-setup.md](sealed-secrets-setup.md)

---

## 문서 목록

### 설계 및 아키텍처

| 파일 | 용도 |
|------|------|
| [PLAN.md](PLAN.md) | 마일스톤별 설계 의도·완료 기준 (초기 기획 문서) |
| [architecture.md](architecture.md) | 전체 아키텍처, 서비스 간 관계, 핵심 설계 결정 및 근거 |
| [adr/](adr/README.md) | MADR 형식 아키텍처 의사결정 기록 (ADR-001 ~ ADR-008) |

### API 및 스키마

| 파일 | 용도 |
|------|------|
| [api.md](api.md) | REST API(Swagger UI/OpenAPI 정본) + WebSocket·레거시 수동 명세 |
| [schema.md](schema.md) | 서비스별 DB 스키마 (PostgreSQL) |
| [kafka.md](kafka.md) | Kafka 토픽 목록, 이벤트 스키마 (Avro), 파티셔닝 전략 |

### 환경 구성 및 운영

| 파일 | 용도 |
|------|------|
| [local-dev.md](local-dev.md) | 로컬 개발 환경 구성 및 docker-compose 운영 가이드 |
| [debezium-connector.md](debezium-connector.md) | Debezium 커넥터 설정 및 등록 방법 |
| [avro-schema.md](avro-schema.md) | Schema Registry 등록 절차, `infra/avro` 스크립트와의 관계 |
| [internal-service-auth.md](internal-service-auth.md) | Gateway ↔ 내부 서비스 간 Pre-shared Secret 인증 설정 가이드 |
| [gke-deployment.md](gke-deployment.md) | GCP 인프라 프로비저닝 및 GKE 배포 실행 순서 |
| [sealed-secrets-setup.md](sealed-secrets-setup.md) | Sealed Secrets Controller 설치 및 모니터링 시크릿 봉인 절차 |
| [e2e-smoke-test.md](e2e-smoke-test.md) | E2E Smoke Test 시나리오, 파일 구조, CD 파이프라인 연동 가이드 |
| [performance/](performance/README.md) | 성능 테스트 측정 절차와 before/after 결과 리포트 |
| [preview-deploy.md](preview-deploy.md) | feature 브랜치를 main 머지 없이 GKE dev 환경에서 검증하는 워크플로 가이드 |
| [observability.md](observability.md) | Prometheus, Grafana, Loki, Tempo, Alloy 구성과 배포 후 검증 방법 |
