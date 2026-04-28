# DB 스키마

각 서비스는 독립된 PostgreSQL DB를 사용합니다.
서비스 간 직접 DB 접근 및 JOIN은 금지되며, 데이터 공유는 Kafka 이벤트로만 합니다.

---

## Auction Service

```sql
CREATE TABLE auctions (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    seller_id   UUID NOT NULL,
    title       VARCHAR(255) NOT NULL,
    description TEXT,
    start_price BIGINT NOT NULL,
    status      VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    -- PENDING: 시작 전 | ONGOING: 진행 중 | CLOSED: 마감 | CANCELLED: 취소
    ends_at     TIMESTAMP NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP NOT NULL DEFAULT now()
);

-- Debezium이 WAL을 읽어 Kafka로 발행하는 Outbox 테이블
CREATE TABLE outbox_events (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type  VARCHAR(50) NOT NULL,   -- 'AUCTION'
    aggregate_id    UUID NOT NULL,
    event_type      VARCHAR(50) NOT NULL,   -- 'AUCTION_CREATED' | 'AUCTION_CLOSED' | 'AUCTION_CANCELLED'
    payload         JSONB NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT now()
);
```

> `currentPrice`, `currentWinnerId`는 이 테이블에 없습니다.
> 최고가는 Kafka Streams State Store에서 관리하며, 조회 시 State Store에서 읽습니다.

---

## Bid Service

```sql
CREATE TABLE bids (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    auction_id  UUID NOT NULL,
    bidder_id   UUID NOT NULL,
    amount      BIGINT NOT NULL,   -- 원 단위 정수 (부동소수점 오차 방지)
    status      VARCHAR(20) NOT NULL DEFAULT 'ACCEPTED',
    -- ACCEPTED: 입찰 수락 | REJECTED: 입찰 거부
    placed_at   TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_bids_auction_id ON bids(auction_id);
CREATE INDEX idx_bids_bidder_id ON bids(bidder_id);

-- Debezium이 WAL을 읽어 Kafka로 발행하는 Outbox 테이블
CREATE TABLE outbox_events (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type  VARCHAR(50) NOT NULL,   -- 'BID'
    aggregate_id    UUID NOT NULL,
    event_type      VARCHAR(50) NOT NULL,   -- 'BID_PLACED' | 'BID_REJECTED'
    payload         JSONB NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT now()
);
```

---

## User Service

```sql
CREATE TABLE users (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email      VARCHAR(255) NOT NULL UNIQUE,
    password   VARCHAR(255) NOT NULL,   -- bcrypt 해시
    nickname   VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

-- Refresh Token Rotation 전략
-- 재발급 시 기존 토큰 revoked=true, 새 토큰 발급
CREATE TABLE refresh_tokens (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID NOT NULL REFERENCES users(id),
    token      VARCHAR(512) NOT NULL UNIQUE,
    expires_at TIMESTAMP NOT NULL,
    revoked    BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_refresh_tokens_token ON refresh_tokens(token);
```

---

## 설계 결정 사항

**금액을 BIGINT로 저장하는 이유**
DECIMAL/FLOAT은 부동소수점 오차가 발생합니다. 금액은 원 단위 정수로 저장하는 것이 실무 관례입니다.

**currentPrice를 auctions 테이블에 두지 않는 이유**
입찰이 동시에 몰릴 때 DB UPDATE 경합이 발생합니다. 최고가는 Kafka Streams State Store에서 관리하고 조회합니다.

**Outbox Table이 서비스마다 있는 이유**
MSA에서 서비스별 DB가 분리되어 있으므로 Outbox도 각 DB에 존재합니다. Debezium이 각 DB의 WAL을 독립적으로 읽습니다.
