# streams/auction-streams/CLAUDE.md

Kafka Streams 기반 실시간 경매 처리 앱. 이 프로젝트의 핵심 컴포넌트.

---

## 도메인 책임

- `bid-events` 소비 → State Store에 경매별 최고가 실시간 갱신
- Windowed Aggregation으로 단위 시간 내 입찰 급증 탐지
- Punctuator로 경매 마감 시각 감지 → `AUCTION_CLOSED` 이벤트 발행
- 처리 불가 이벤트를 Dead Letter Queue(DLQ) 토픽으로 라우팅
- Interactive Queries API 제공 (bid-service, auction-service가 State Store 조회 시 사용)

---

## Kafka Streams 토폴로지

```
bid-events (KStream)
  └─ filter: 유효한 BID_PLACED 이벤트만
       └─ groupByKey (auctionId)
            └─ aggregate → State Store (auction-highest-bid)
                 └─ to: notification-events (BID_UPDATED 이벤트)

auction-events (KStream)
  └─ filter: AUCTION_CREATED 이벤트
       └─ Punctuator 등록 (경매 마감 시각)
            └─ 마감 시 State Store 조회 → AUCTION_CLOSED + AUCTION_WON 이벤트
                 └─ to: notification-events
```

---

## State Store

| Store 이름 | Key | Value | 용도 |
|-----------|-----|-------|------|
| `auction-highest-bid` | auctionId | `{highestBid, highestBidderId, bidCount}` | 실시간 최고가 |
| `auction-metadata` | auctionId | `{closedAt, status}` | 마감 타이머용 |

- 상태는 RocksDB 기반 영속 State Store를 사용한다 (재시작 시 복구 가능).
- State Store를 직접 DB로 사용하는 게 이 설계의 핵심. **Bid Service가 DB를 조회하지 않는다.**

---

## Interactive Queries API

다른 서비스가 State Store를 조회할 수 있도록 REST API를 노출한다.

| Method | Path | 설명 |
|--------|------|------|
| GET | `/state/auctions/{auctionId}/highest-bid` | 현재 최고가 조회 |
| GET | `/state/auctions/{auctionId}/status` | 경매 상태 조회 |

멀티 인스턴스 환경에서 특정 auctionId의 State가 다른 인스턴스에 있을 경우 내부 라우팅으로 처리한다 (`StreamsMetadataForKey`).

---

## Dead Letter Queue

처리 중 예외 발생 시 해당 이벤트를 `bid-events-dlq` 토픽으로 이동한다.
DLQ 이벤트에는 원본 이벤트 + 실패 원인 + 타임스탬프를 포함한다.
프로덕션 환경에서 DLQ 이벤트는 별도 모니터링 대상이다.

---

## 핵심 설계 주의사항

- Kafka Streams는 exactly-once semantics(`processing.guarantee=exactly_once_v2`)로 설정한다.
- `KafkaStreams` 인스턴스는 Spring 컨테이너 라이프사이클에 맞춰 시작/종료한다 (`SmartLifecycle`).
- Punctuator는 stream time 기반(`PunctuationType.STREAM_TIME`)으로 동작한다. wall clock 사용 시 이벤트 없으면 발화 안 됨에 주의.
- 스키마는 Confluent Schema Registry + Avro를 사용한다. 수동 JSON 직렬화 금지.

---

## 소비/발행 토픽

| 토픽 | 방향 | 설명 |
|------|------|------|
| `bid-events` | consume | 입찰 이벤트 |
| `auction-events` | consume | 경매 생성/상태 변경 이벤트 |
| `notification-events` | produce | 알림 이벤트 (Outbox 아님, Streams가 직접 발행) |
| `bid-events-dlq` | produce | 처리 실패 이벤트 |

> `notification-events`는 Outbox가 아닌 Kafka Streams가 직접 발행한다.
> Streams 앱 자체가 exactly-once를 보장하므로 여기서는 Outbox Pattern이 불필요하다.

---

## 관련 문서

- docs/kafka.md — 토픽 스키마 상세
- docs/architecture.md — 전체 흐름 다이어그램
