---
status: "accepted"
date: 2026-05-18
decision-makers: jaehoon9875
---

# Outbox Pattern + Debezium CDC로 이벤트 발행

## Context and Problem Statement

Auction Service와 Bid Service는 비즈니스 로직 처리 후 Kafka에 이벤트를 발행해야 합니다.
"DB 저장 → Kafka 발행"을 순차적으로 처리하면, 저장 성공 후 발행 직전 장애가 발생했을 때 이벤트가 영구적으로 유실됩니다.

**DB 저장과 Kafka 이벤트 발행을 원자적으로 처리하려면 어떤 방법을 선택해야 하는가?**

## Decision Drivers

- 이벤트 유실 금지: 입찰·경매 상태 이벤트가 유실되면 낙찰자 결정, 알림 등 downstream 처리 전체에 영향
- DB 저장과 이벤트 발행의 원자성 보장 (두 작업이 항상 함께 성공하거나 함께 실패)
- Kafka 장애 시 서비스 정상 운영 가능 (Kafka에 직접 의존하지 않아야 함)

## Considered Options

- Outbox Pattern + Debezium CDC
- Kafka 트랜잭션 프로듀서로 직접 발행
- Polling Publisher (Debezium 없이 Outbox 테이블 직접 폴링)

## Decision Outcome

Chosen option: **"Outbox Pattern + Debezium CDC"**, because DB 트랜잭션 안에서 `outbox_events` 테이블에 이벤트를 함께 저장하고, Debezium이 PostgreSQL WAL을 비동기로 읽어 Kafka에 발행하므로 애플리케이션 코드 변경 없이 원자성이 구조적으로 보장되기 때문.

```
DB 트랜잭션 (원자적)
├── auctions / bids 테이블 저장
└── outbox_events 테이블 저장
         ↓
    Debezium (WAL 읽기)
         ↓
    Kafka 토픽 발행
```

### Consequences

- Good, because 이벤트 유실이 구조적으로 불가능 (DB 저장 = 이벤트 보장)
- Good, because Kafka 장애 시 서비스는 정상 운영되고, 복구 후 Debezium이 밀린 이벤트를 순차 발행
- Bad, because Debezium Connector 등록·모니터링 운영 부담 증가
- Bad, because PostgreSQL `wal_level=logical` 설정 및 Replication Slot 관리 필요
- Bad, because 이벤트 발행이 비동기이므로 즉각적인 발행 보장 불가 (at-least-once, 짧은 지연 있음)

### Confirmation

Debezium Connector가 정상 등록되고 `outbox_events` INSERT가 Kafka 토픽에 발행되는지 확인.
관련 설정: [debezium-connector.md](../debezium-connector.md)

## Pros and Cons of the Options

### Outbox Pattern + Debezium CDC

- Good, because DB 트랜잭션과 이벤트 저장이 하나의 원자적 단위로 처리됨
- Good, because Debezium이 WAL 오프셋을 추적하여 재시작 후에도 유실 없이 재처리
- Good, because 애플리케이션이 Kafka 클라이언트에 직접 의존하지 않아 결합도 낮음
- Bad, because Debezium, Schema Registry, Kafka 등 인프라 의존성이 많아 로컬 개발 환경이 무거움

### Kafka 트랜잭션 프로듀서로 직접 발행

- Good, because 별도 인프라(Debezium) 없이 애플리케이션 코드만으로 구현 가능
- Bad, because DB 커밋과 Kafka 발행이 별도 연산이므로 그 사이 장애 시 이벤트 유실 발생
- Bad, because Kafka 장애 시 서비스 자체가 영향을 받음

### Polling Publisher (Debezium 없이 Outbox 테이블 직접 폴링)

- Good, because Debezium 없이 Outbox Pattern 구현 가능, 인프라 의존성 감소
- Neutral, because 구현이 단순하지만 폴링 스케줄러 관리 필요
- Bad, because 폴링 주기만큼 이벤트 발행이 지연됨
- Bad, because 폴링이 잦을수록 DB에 불필요한 부하 발생
