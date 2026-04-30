# docs/CLAUDE.md

프로젝트 설계 문서, 의사결정 기록, API 명세, 이슈 추적 디렉토리.

## 문서 목록

- PLAN.md: 마일스톤별 구현 체크리스트. 현재 진행 단계 파악 시 최우선 참조
- ISSUES.md: 진행 중 이슈, 미해결 항목, 보류 결정 사항 추적
- architecture.md: 전체 아키텍처, 서비스 간 관계, 핵심 설계 결정 및 근거
- local-dev.md: 로컬 개발 환경 구성 및 docker-compose 운영 가이드
- api.md: REST API + WebSocket 엔드포인트 명세
- schema.md: 서비스별 DB 스키마 (PostgreSQL)
- kafka.md: Kafka 토픽 목록, 이벤트 스키마 (Avro), 파티셔닝 전략
- ai-workflows.md: AI 자동화 워크플로우 목록 및 구현 계획 (M7 도입 예정)

## 문서 업데이트 규칙

- architecture.md: 서비스/인프라 구조 변경 시 반드시 업데이트
- kafka.md: 토픽 추가/스키마 변경 시 업데이트. Avro 스키마와 항상 일치
- schema.md: DB 마이그레이션 추가 시 반영
- ISSUES.md: 이슈 해결 시 해결 날짜와 방법 기록 후 해결 이슈 섹션으로 이동
- 미완성 항목: <!-- TODO: ... --> 주석으로 표시
- 의사결정 기록: 결정 내용과 선택하지 않은 대안 및 이유를 함께 기록
