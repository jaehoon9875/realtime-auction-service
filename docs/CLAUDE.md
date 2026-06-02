# docs/CLAUDE.md

프로젝트 설계 문서, 의사결정 기록, API 명세 디렉토리.

## 문서 목록

- PLAN.md: 마일스톤별 설계 의도·완료 기준 기록 (초기 기획 문서). 진행 상태는 GitHub Milestones에서 관리
- architecture.md: 전체 아키텍처, 서비스 간 관계, 핵심 설계 결정 및 근거(경매 생명주기·마감 정책 포함)
- local-dev.md: 로컬 개발 환경 구성 및 docker-compose 운영 가이드
- gke-deployment.md: GCP 인프라 프로비저닝 및 GKE 배포 실행 순서
- sealed-secrets-setup.md: Sealed Secrets Controller 설치 및 모니터링 시크릿 봉인 절차
- api.md: REST는 Swagger UI/OpenAPI 정본, WebSocket·레거시 수동 명세 포함
- schema.md: 서비스별 DB 스키마 (PostgreSQL)
- kafka.md: Kafka 토픽 목록, 이벤트 스키마 (Avro), 파티셔닝 전략
- avro-schema.md: Schema Registry 등록 절차, `infra/avro` 스크립트와의 관계
- debezium-connector.md: Debezium 커넥터 설정 및 등록 방법
- internal-service-auth.md: Gateway ↔ 내부 서비스 간 Pre-shared Secret 인증 설정 가이드
- adr/: MADR 형식 아키텍처 의사결정 기록. 각 결정의 Context·선택 근거·고려한 대안 포함 (adr/README.md 참고)
- e2e-smoke-test.md: E2E Smoke Test 설명, 로컬 실행 방법, GitHub Secrets/Variables 전체 등록 가이드
- performance/: 성능 테스트 측정 절차와 before/after 결과 리포트
- preview-deploy.md: feature 브랜치를 main 머지 없이 GKE dev 환경에서 검증하는 워크플로 가이드
- observability.md: Prometheus, Grafana, Loki, Tempo, Alloy 구성과 배포 후 검증 방법
- ../CHANGELOG.md: 마일스톤별 변경 이력 (Keep a Changelog 형식, 루트에 위치)

## 문서 업데이트 규칙

- architecture.md: 서비스/인프라 구조 변경 시 반드시 업데이트
- adr/: 새로운 기술 결정 또는 기존 결정 번복 시 ADR 추가 또는 기존 ADR status를 `superseded by ADR-XXXX`로 변경
- kafka.md: 토픽 추가/스키마 변경 시 업데이트. Avro 스키마와 항상 일치
- schema.md: DB 마이그레이션 추가 시 반영
- 이슈 발생 시 GitHub Issues에 등록. 해결된 이슈는 GitHub에서 Close 처리
- CHANGELOG.md: 마일스톤 완료 시 `[Unreleased]` 바로 아래에 새 버전 항목 추가 (버전: M1=0.1.0, M6=0.6.0 순으로 증가)
- 미완성 항목: 문서 본문에 가시적인 평문으로 표시 (예: `(M7 도입 예정)`)
- 의사결정 기록: 결정 내용과 선택하지 않은 대안 및 이유를 함께 기록
