# docs/CLAUDE.md

이 디렉토리는 프로젝트 설계 문서, 의사결정 기록, API 명세, 이슈 추적을 관리합니다.

---

## 문서 목록

| 파일 | 용도 |
|------|------|
| [PLAN.md](PLAN.md) | 마일스톤별 구현 체크리스트. 현재 진행 단계 파악 시 최우선 참조 |
| [ISSUES.md](ISSUES.md) | 진행 중 이슈, 미해결 항목, 보류 결정 사항 추적 |
| [architecture.md](architecture.md) | 전체 아키텍처, 서비스 간 관계, 핵심 설계 결정 및 근거 |
| [api.md](api.md) | REST API + WebSocket 엔드포인트 명세 |
| [schema.md](schema.md) | 서비스별 DB 스키마 (PostgreSQL) |
| [kafka.md](kafka.md) | Kafka 토픽 목록, 이벤트 스키마 (Avro), 파티셔닝 전략 |

---

## 문서 작성 규칙

- `architecture.md`는 서비스/인프라 구조 변경 시 **반드시** 업데이트한다.
- `kafka.md`는 토픽 추가/스키마 변경 시 함께 업데이트한다. Avro 스키마와 항상 일치해야 한다.
- `schema.md`는 Alembic 마이그레이션 추가 시 함께 반영한다.
- `ISSUES.md`에서 이슈가 해결되면 해결 날짜와 방법을 기록한 뒤 해결 이슈 섹션으로 이동한다.
- 미완성 항목은 `<!-- TODO: ... -->` 주석으로 표시한다.
- 의사결정 기록 시 **결정 내용**과 **선택하지 않은 대안 및 이유**를 함께 남긴다.
