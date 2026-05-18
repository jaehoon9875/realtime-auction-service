# Architecture Decision Records (ADR)

MADR(Markdown Architectural Decision Records) 형식으로 작성합니다.
형식 참고: [adr.github.io/madr](https://adr.github.io/madr/)

## 목록

| ID | 제목 | 상태 | 날짜 |
|----|------|------|------|
| [ADR-001](001-outbox-pattern-debezium.md) | Outbox Pattern + Debezium CDC로 이벤트 발행 | Accepted | 2026-05-18 |
| [ADR-002](002-kafka-streams-state-store.md) | Kafka Streams State Store로 경매 최고가 관리 | Accepted | 2026-05-18 |
| [ADR-003](003-redis-websocket-session.md) | Redis 기반 WebSocket 세션 공유 | Proposed | 2026-05-18 |
| [ADR-004](004-avro-schema-registry.md) | Avro + Schema Registry로 이벤트 스키마 관리 | Accepted | 2026-05-18 |
| [ADR-005](005-resilience4j-circuit-breaker.md) | Resilience4j Circuit Breaker로 장애 격리 | Accepted | 2026-05-18 |

## Status 정의

- **Proposed**: 결정 검토 중 (미구현)
- **Accepted**: 결정 확정 및 구현 완료
- **Deprecated**: 더 이상 유효하지 않음
- **Superseded by ADR-XXXX**: 다른 ADR로 대체됨
