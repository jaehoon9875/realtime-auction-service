# Kafka 설계

## 토픽 구성

| 토픽 | Partitions | Replication | Retention | 용도 |
|------|-----------|-------------|-----------|------|
| auction-events | 3 | 2 | 7d | 경매 생성/마감/취소 |
| bid-events | 6 | 2 | 7d | 입찰 발생 |
| notification-events | 3 | 2 | 3d | 알림 발송 대상 |
| auction-dead-letter | 1 | 1 | 30d | auction-events 처리 실패 |
| bid-dead-letter | 1 | 1 | 30d | bid-events 처리 실패 |

> `bid-events`를 partition 6개로 설정한 이유: 경매 진행 중 입찰이 가장 집중되는 토픽으로, 처리량 확보를 위해 다른 토픽보다 많은 파티션을 할당합니다.

---

## 이벤트 스키마 (Avro)

Schema Registry에 등록하여 버전 관리합니다.

### auction-events

```json
{
  "type": "record",
  "name": "AuctionEvent",
  "fields": [
    { "name": "eventId",      "type": "string" },
    { "name": "eventType",    "type": "string" },
    { "name": "auctionId",    "type": "string" },
    { "name": "sellerId",     "type": "string" },
    { "name": "status",       "type": "string" },
    { "name": "title",        "type": "string" },
    { "name": "startPrice",   "type": "long" },
    { "name": "currentPrice", "type": ["null", "long"], "default": null },
    { "name": "endsAt",       "type": "long" },
    { "name": "occurredAt",   "type": "long" }
  ]
}
```

원본 Avro 파일(Schema Registry 등록용): [`infra/avro/AuctionEvent.avsc`](../infra/avro/AuctionEvent.avsc) — 필드는 동일하며 `namespace` 등 메타데이터가 포함됩니다. 등록 절차는 [docs/avro-schema.md](./avro-schema.md)를 참고하세요.

| eventType | 발행 시점 |
|-----------|----------|
| AUCTION_CREATED | 경매 생성 |
| AUCTION_CLOSED | 경매 마감 (Punctuator) |
| AUCTION_CANCELLED | 경매 취소 |

---

### bid-events

```json
{
  "type": "record",
  "name": "BidEvent",
  "fields": [
    { "name": "eventId",    "type": "string" },
    { "name": "eventType",  "type": "string" },
    { "name": "bidId",      "type": "string" },
    { "name": "auctionId",  "type": "string" },
    { "name": "bidderId",   "type": "string" },
    { "name": "amount",     "type": "long" },
    { "name": "occurredAt", "type": "long" }
  ]
}
```

| eventType | 발행 시점 |
|-----------|----------|
| BID_PLACED | 입찰 수락 |
| BID_REJECTED | 입찰 거부 |

---

### notification-events

```json
{
  "type": "record",
  "name": "NotificationEvent",
  "fields": [
    { "name": "eventId",          "type": "string" },
    { "name": "notificationType", "type": "string" },
    { "name": "targetUserId",     "type": "string" },
    { "name": "auctionId",        "type": "string" },
    { "name": "payload",          "type": { "type": "map", "values": "string" } },
    { "name": "occurredAt",       "type": "long" }
  ]
}
```

| notificationType | 발행 시점 | 대상 |
|-----------------|----------|------|
| OUTBID | 더 높은 입찰 발생 | 기존 최고 입찰자 |
| AUCTION_WON | 경매 마감 + 낙찰 | 최종 낙찰자 |
| AUCTION_CLOSED | 경매 마감 | 경매 구독자 전체 |

---

## Kafka Streams 처리 흐름

```
bid-events
    │
    ├─▶ [KTable] State Store (RocksDB)
    │     key: auctionId
    │     value: { currentPrice, currentWinnerId, bidCount }
    │     → GET /auctions/{id} 조회 시 여기서 읽음
    │
    ├─▶ [Windowed KStream] 입찰 급증 탐지
    │     1분 tumbling window
    │     동일 auctionId 입찰 10회 이상 → 이상 입찰 플래그 로깅
    │
    └─▶ [KStream join auction-events]
          마감 이벤트 발생 시 최고가 확정
          → notification-events 발행 (AUCTION_WON, OUTBID)

auction-events
    │
    └─▶ [Punctuator]
          30초 주기로 endsAt 경과 경매 체크
          → AUCTION_CLOSED 이벤트 발행
          → notification-events 발행 (AUCTION_CLOSED)
```

---

## Dead Letter Queue

처리 실패한 이벤트는 DLQ 토픽으로 이동하여 유실 없이 보관합니다.

```
bid-events 처리 실패
    │
    └─▶ bid-dead-letter (retention 30d)
          { originalEvent, errorMessage, failedAt }
```

DLQ에 쌓인 이벤트는 수동 또는 별도 프로세스로 재처리합니다.

---

## Debezium Connector 설정

Auction Service와 Bid Service 각각 connector를 등록합니다.

```json
{
  "name": "auction-outbox-connector",
  "config": {
    "connector.class": "io.debezium.connector.postgresql.PostgresConnector",
    "database.hostname": "auction-db",
    "database.port": "5432",
    "database.user": "debezium",
    "database.password": "${DEBEZIUM_PASSWORD}",
    "database.dbname": "auction",
    "table.include.list": "public.outbox_events",
    "topic.prefix": "auction",
    "plugin.name": "pgoutput",
    "transforms": "outbox",
    "transforms.outbox.type": "io.debezium.transforms.outbox.EventRouter",
    "transforms.outbox.table.field.event.key": "aggregate_id",
    "transforms.outbox.route.topic.replacement": "auction-events"
  }
}
```
