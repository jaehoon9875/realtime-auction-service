# Architecture

## 개요

realtime-auction-service는 Kafka Streams 기반 실시간 경매 서비스입니다.
MSA 구조로 서비스를 분리하고, Debezium CDC + Outbox Pattern으로 이벤트 유실 없는 발행을 보장합니다.
Kafka Streams State Store로 실시간 최고가를 관리하고, WebSocket으로 클라이언트에 실시간 알림을 전달합니다.

---

## 전체 아키텍처

```
[Client]
   │
   ├─ REST API (HTTP/JSON)
   └─ WebSocket (/ws/auctions/{id}, /ws/users/me)
          │
   [API Gateway] (Spring Cloud Gateway)
   - 라우팅
   - JWT 인증 필터
          │
   ┌──────┼──────────────┬──────────────────┐
   │      │              │                  │
[Auction] [Bid]       [User]         [Notification]
[Service] [Service]  [Service]       [Service]
   │         │        JWT 발급        Kafka 소비
   │         │                       WebSocket push
   └────┬────┘                            ▲
        │                                 │
   Outbox Table (각 서비스 DB)             │
        │                                 │
   [Debezium CDC]                         │
   WAL 기반 이벤트 캡처                    │
        │                                 │
      [Kafka]                             │
   auction-events                         │
   bid-events ──▶ [Kafka Streams] ────────┘
                       │
                  State Store (RocksDB)
                  - 경매별 최고가 관리
                  - Windowed Aggregation (이상 탐지)
                  - Punctuator (마감 타이머)
                       │
                 notification-events
                       │
                  auction-dead-letter
                  bid-dead-letter
```

---

## 서비스 구성

| 서비스 | 역할 | Port | DB |
|--------|------|------|----|
| API Gateway | 라우팅, JWT 인증 필터 | 8080 | - |
| Auction Service | 경매 CRUD, Outbox 발행 | 8081 | PostgreSQL |
| Bid Service | 입찰 처리, 유효성 검증 | 8082 | PostgreSQL |
| User Service | 회원가입/로그인, JWT 발급 | 8083 | PostgreSQL |
| Notification Service | Kafka 소비 → WebSocket push | 8084 | - |
| Auction Streams | 실시간 집계, 마감 처리 | - | State Store (RocksDB) |

---

## 핵심 기술 결정

### 1. Outbox Pattern + Debezium CDC

**문제**: Auction/Bid Service가 DB 저장 후 Kafka에 직접 발행하면, 그 사이 장애 시 이벤트가 유실됩니다.

**해결**: Outbox Table에 이벤트를 DB 트랜잭션과 함께 저장하고, Debezium이 PostgreSQL WAL을 읽어 Kafka로 발행합니다. DB 저장과 이벤트 발행이 항상 일관성을 유지합니다.

```
DB 트랜잭션 (원자적)
├── auctions 테이블 저장
└── outbox_events 테이블 저장
        │
   Debezium (WAL 읽기)
        │
   Kafka auction-events 토픽
```

### 2. Kafka Streams State Store

**문제**: 입찰이 동시에 몰릴 때 DB의 currentPrice UPDATE 경합이 발생합니다.

**해결**: 현재 최고가를 DB가 아닌 Kafka Streams 내장 State Store(RocksDB)에서 관리합니다.
`GET /auctions/{id}`의 currentPrice는 DB가 아닌 State Store에서 조회합니다.

```
bid-events 토픽
      │
Kafka Streams
      │
State Store (RocksDB)
  key: auctionId
  value: { currentPrice, currentWinnerId, bidCount }
      │
GET /auctions/{id} → State Store 조회
```

### 3. Redis 기반 WebSocket 세션 공유

**문제**: Notification Service가 여러 인스턴스로 스케일아웃될 때, 특정 사용자의 WebSocket 세션이 어느 인스턴스에 연결되어 있는지 알 수 없습니다.

**해결**: Redis에 세션 정보를 공유하여 어떤 인스턴스에서도 정확한 사용자에게 알림을 전달합니다.

```
Notification Service (Instance 1) ─┐
Notification Service (Instance 2) ─┼─▶ Redis (세션 공유)
Notification Service (Instance 3) ─┘
```

### 4. Schema Registry + Avro

**문제**: 서비스 간 Kafka 이벤트 스키마가 변경될 때 하위 호환성을 보장해야 합니다.

**해결**: Confluent Schema Registry와 Avro를 사용하여 스키마 버전을 관리하고, 서비스 간 이벤트 계약을 명시적으로 유지합니다.

### 5. Circuit Breaker (Resilience4j)

**문제**: Bid Service가 Auction Service에 경매 정보를 조회할 때, Auction Service 장애가 Bid Service로 전파될 수 있습니다.

**해결**: Circuit Breaker를 적용하여 Auction Service 장애 시 빠르게 실패 처리하고 장애 전파를 차단합니다.

---

## Kafka Streams 처리 흐름

```
bid-events
    │
    ├─▶ [KTable] State Store
    │     경매별 최고가 실시간 갱신
    │     key: auctionId
    │     value: { currentPrice, currentWinnerId, bidCount }
    │
    ├─▶ [Windowed KStream] 입찰 급증 탐지
    │     1분 tumbling window
    │     동일 경매 10회 이상 → 이상 입찰 플래그
    │
    └─▶ [KStream join auction-events] 마감 처리
          낙찰자 확정 → notification-events 발행
          (AUCTION_WON, OUTBID)

auction-events
    │
    └─▶ [Punctuator] 마감 타이머
          30초마다 endsAt 체크
          마감 경매 → AUCTION_CLOSED 발행
```

---

## 인프라 구성

```
[GKE - cloud-sre-platform 연계]
    │
    ├── Strimzi Kafka Operator   # Kafka 클러스터 관리
    ├── ArgoCD                   # GitOps 배포
    ├── Prometheus + Grafana     # 모니터링
    └── 각 서비스 Deployment
```

로컬 개발은 `docker-compose.yml`로 전체 인프라를 로컬에서 구동합니다.
운영 배포는 GKE 위에서 ArgoCD GitOps로 관리합니다.
