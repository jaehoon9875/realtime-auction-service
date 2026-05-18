---
status: "accepted"
date: 2026-05-18
decision-makers: jaehoon9875
---

# Kafka Streams State Store로 경매 최고가 관리

## Context and Problem Statement

경매 진행 중 다수의 입찰이 동시에 발생할 수 있습니다.
`currentPrice`를 DB에서 관리하면 동시 입찰 시 UPDATE 경합이 발생하고, 매 조회마다 DB를 거쳐야 해 레이턴시가 높아집니다.

**경매별 실시간 최고가를 어디서 관리해야 성능과 일관성을 동시에 확보할 수 있는가?**

## Decision Drivers

- 동시 입찰 경합 없이 최고가를 단일 진실 원본(Single Source of Truth)으로 유지
- 낮은 조회 레이턴시 (실시간 경매 UI 갱신에 민감)
- Kafka Streams 파이프라인과의 자연스러운 통합

## Considered Options

- Kafka Streams 내장 State Store (RocksDB)
- DB의 `currentPrice` 컬럼 직접 관리
- Redis 캐시로 최고가 관리

## Decision Outcome

Chosen option: **"Kafka Streams 내장 State Store (RocksDB)"**, because `bid-events` 토픽을 Kafka Streams가 소비하면서 동일한 파이프라인 안에서 State Store를 갱신하므로, DB 경합 없이 순차적으로 최고가를 갱신할 수 있고, Interactive Query API로 낮은 레이턴시 조회가 가능하기 때문.

```text
bid-events 토픽
      ↓
 Kafka Streams
      ↓
 State Store (RocksDB)
   key: auctionId
   value: { currentPrice, currentWinnerId, bidCount }
      ↑
 GET /auctions/{id} 조회
```

### Consequences

- Good, because Kafka Streams 파티션 단위로 처리되므로 동일 경매의 입찰이 순차 처리되어 경합 없음
- Good, because State Store 직접 조회로 DB 부하 없이 낮은 레이턴시 달성
- Bad, because Auction Streams 앱 재시작 시 State Store 복구(changelog 재처리) 시간 발생
- Bad, because State Store는 Kafka Streams 앱 내부에 있으므로 다른 서비스가 직접 읽을 수 없음 (Interactive Query REST API 필요)
- Bad, because Streams 앱이 단일 인스턴스이면 해당 앱이 SPOF가 됨

### Confirmation

`GET /auctions/{id}` 응답의 `currentPrice`가 State Store에서 반환되고, DB `auctions.current_price`와 다를 수 있음을 확인.
Interactive Query 엔드포인트: Auction Streams 앱 `GET /internal/auctions/{id}/current-price`

## Pros and Cons of the Options

### Kafka Streams 내장 State Store (RocksDB)

- Good, because 동일 파티션의 이벤트가 순차 처리되므로 동시성 문제가 구조적으로 해결됨
- Good, because DB를 거치지 않아 조회 레이턴시가 낮음
- Good, because Kafka 파이프라인과 자연스럽게 통합됨 (별도 캐시 동기화 로직 불필요)
- Bad, because Streams 앱 재시작 시 changelog 토픽 재처리로 인한 복구 지연
- Bad, because State Store 조회를 위한 Interactive Query REST API를 별도 구현해야 함

### DB의 `currentPrice` 컬럼 직접 관리

- Good, because 별도 인프라 없이 기존 DB 그대로 사용 가능
- Good, because 구현이 단순하고 트랜잭션으로 일관성 보장 가능
- Bad, because 동시 입찰 시 `UPDATE` 경합으로 성능 저하 및 데드락 위험
- Bad, because 고부하 입찰 구간에서 DB가 병목이 됨

### Redis 캐시로 최고가 관리

- Good, because 인메모리 조회로 낮은 레이턴시 달성 가능
- Neutral, because Redis는 이미 WebSocket 세션 공유([ADR-003](003-redis-websocket-session.md))로 도입 예정이므로 추가 인프라 부담 없음
- Bad, because DB 또는 Kafka와 Redis 간 캐시 동기화 로직을 별도 구현해야 함
- Bad, because 동기화 실패 시 캐시와 실제 최고가가 불일치하는 문제 발생 가능
