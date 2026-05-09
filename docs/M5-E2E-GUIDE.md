# M5 E2E 검증 가이드

> 새 챗 세션 인계용 임시 파일. 완료 후 삭제.

---

## Phase 1. 인프라 기동

```bash
# infra/ 디렉토리에서 실행
cd infra

# 이미 .env 없으면 먼저 복사 후 값 채우기
cp .env.example .env    # JWT 키쌍, DEBEZIUM_PASSWORD, INTERNAL_REQUEST_SECRET 등

# 전체 인프라 기동 (Kafka, Schema Registry, Debezium, Postgres ×3, Redis)
docker-compose up -d

# 기동 완료 확인 (~2분 소요)
docker-compose ps
```

모든 컨테이너 STATUS가 `healthy` 또는 `Up`인지 확인.

---

## Phase 2. Kafka 토픽 생성

```bash
docker exec -it kafka bash

# 이하 kafka 컨테이너 내부에서 실행
kafka-topics --bootstrap-server localhost:9092 --create --topic auction-events      --partitions 3 --replication-factor 1
kafka-topics --bootstrap-server localhost:9092 --create --topic bid-events          --partitions 6 --replication-factor 1
kafka-topics --bootstrap-server localhost:9092 --create --topic notification-events --partitions 3 --replication-factor 1
kafka-topics --bootstrap-server localhost:9092 --create --topic auction-dead-letter --partitions 1 --replication-factor 1
kafka-topics --bootstrap-server localhost:9092 --create --topic bid-dead-letter     --partitions 1 --replication-factor 1

# 생성 확인
kafka-topics --bootstrap-server localhost:9092 --list

exit
```

---

## Phase 3. Avro 스키마 등록

```bash
cd infra/avro
./register-schemas.sh
```

성공하면 각 subject에 `{"id": 1}` 형태로 schema ID가 반환됩니다.

```bash
# 등록 확인
curl http://localhost:8085/subjects
# 결과: ["auction-events-value","bid-events-value","notification-events-value"]
```

---

## Phase 4. Debezium 커넥터 등록

```bash
cd infra/debezium
./register-connectors.sh

# 커넥터 상태 확인
curl http://localhost:8083/connectors/auction-outbox-connector/status
curl http://localhost:8083/connectors/bid-outbox-connector/status
```

`"state":"RUNNING"` 이면 CDC 파이프라인이 동작 중.

---

## Phase 5. 서비스 기동

### 호스트에서 `bootRun` 할 때 환경 변수

- `docker compose`는 `infra/.env`를 자동으로 읽지만, **호스트에서 `./gradlew … bootRun`으로 띄우는 JVM에는 전달되지 않습니다.**
- `./gradlew test`로 돌리는 통합 테스트(`UserIntegrationTest` 등)는 **Testcontainers + `@DynamicPropertySource`**로 DB·Redis·JWT를 주입하므로, 셸에 `POSTGRES_*`를 올려 두지 않아도 됩니다.
- E2E처럼 서비스를 로컬 프로세스로 직접 띄울 때만 `infra/.env`를 셸에 로드하면 됩니다. **저장소 루트**에서 아래를 각 터미널마다 한 번 실행한 뒤 `bootRun` 하면 됩니다.

```bash
# 저장소 루트에서 (Phase 1에서 만든 infra/.env 사용)
set -a && source infra/.env && set +a
```

`set -a`는 이후 export 없이도 `.env` 안의 `KEY=value`가 자동 export 되게 합니다. zsh/bash 모두 동일하게 쓸 수 있습니다.

터미널을 5개 열어서 각각 실행합니다.

```bash
# 터미널 1 — user-service
./gradlew :services:user-service:bootRun

# 터미널 2 — auction-service
AUCTION_STREAMS_BASE_URL=http://localhost:8086 \
./gradlew :services:auction-service:bootRun

# 터미널 3 — bid-service
AUCTION_SERVICE_BASE_URL=http://localhost:{auction-service-port} \
AUCTION_STREAMS_BASE_URL=http://localhost:8086 \
./gradlew :services:bid-service:bootRun

# 터미널 4 — api-gateway
./gradlew :services:api-gateway:bootRun

# 터미널 5 — auction-streams (Schema Registry 외부 포트는 8085)
SCHEMA_REGISTRY_URL=http://localhost:8085 \
./gradlew :streams:auction-streams:bootRun
```

> auction-streams는 포트 **8086**으로 기동됩니다 (streams/auction-streams/src/main/resources/application.yml 기준).

---

## Phase 6. API 호출 시나리오

이후 모든 요청은 API Gateway(`localhost:8080`)를 경유합니다.

### 6-1. 회원가입 + 로그인

```bash
# 판매자 회원가입
curl -X POST http://localhost:8080/api/users/signup \
  -H "Content-Type: application/json" \
  -d '{"email":"seller@test.com","password":"password1234","nickname":"판매자"}'

# 입찰자1 회원가입
curl -X POST http://localhost:8080/api/users/signup \
  -H "Content-Type: application/json" \
  -d '{"email":"bidder@test.com","password":"password1234","nickname":"입찰자"}'

# 입찰자2 회원가입
curl -X POST http://localhost:8080/api/users/signup \
  -H "Content-Type: application/json" \
  -d '{"email":"bidder2@test.com","password":"password1234","nickname":"입찰자2"}'

# 로그인 → accessToken 획득
SELLER_TOKEN=$(curl -s -X POST http://localhost:8080/api/users/login \
  -H "Content-Type: application/json" \
  -d '{"email":"seller@test.com","password":"password1234"}' \
  | jq -r '.accessToken')

BIDDER_TOKEN=$(curl -s -X POST http://localhost:8080/api/users/login \
  -H "Content-Type: application/json" \
  -d '{"email":"bidder@test.com","password":"password1234"}' \
  | jq -r '.accessToken')

BIDDER2_TOKEN=$(curl -s -X POST http://localhost:8080/api/users/login \
  -H "Content-Type: application/json" \
  -d '{"email":"bidder2@test.com","password":"password1234"}' \
  | jq -r '.accessToken')
```

---

### 6-2. 경매 생성

**포인트:** `endsAt`을 지금으로부터 **3~5분 뒤**로 설정합니다.
Punctuator가 30초 주기이므로, endsAt 경과 후 최대 30초 내에 마감 이벤트가 발행됩니다.

```bash
# endsAt = 지금 + 5분 (직접 날짜 수정 후 실행)
AUCTION_ID=$(curl -s -X POST http://localhost:8080/api/auctions \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $SELLER_TOKEN" \
  -d '{
    "title": "테스트 경매 맥북",
    "description": "E2E 검증용",
    "startPrice": 100000,
    "endsAt": "2026-05-09T12:05:00"
  }' | jq -r '.auctionId')

echo "auctionId: $AUCTION_ID"
```

**이 시점에 확인해야 할 것:**
- Debezium이 `outbox_events` → `auction-events` 토픽으로 `AUCTION_CREATED` 발행
- auction-streams가 이를 소비해 `auction-metadata` Store에 저장

```bash
# State Store 조회 — highestBid가 startPrice(100000)여야 함
curl http://localhost:8086/state/auctions/$AUCTION_ID/highest-bid
# 기대 응답: {"auctionId":"...","highestBid":100000,"highestBidderId":null,"bidCount":0}
```

---

### 6-3. 경매 상세 조회 (auction-service → State Store 연동 확인)

```bash
curl http://localhost:8080/api/auctions/$AUCTION_ID
# currentPrice 필드가 100000으로 나오면 auction-service ↔ auction-streams 연동 정상
```

---

### 6-4. 첫 번째 입찰

```bash
curl -X POST http://localhost:8080/api/bids \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $BIDDER_TOKEN" \
  -d "{\"auctionId\":\"$AUCTION_ID\",\"amount\":150000}"

# State Store 갱신 확인
curl http://localhost:8086/state/auctions/$AUCTION_ID/highest-bid
# 기대: {"highestBid":150000,"highestBidderId":"...","bidCount":1}
```

---

### 6-5. 두 번째 입찰 (OUTBID 이벤트 발생)

입찰자2가 더 높은 금액으로 입찰 → 입찰자1에게 OUTBID 알림 발행:

```bash
curl -X POST http://localhost:8080/api/bids \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $BIDDER2_TOKEN" \
  -d "{\"auctionId\":\"$AUCTION_ID\",\"amount\":200000}"

# State Store 갱신 확인
curl http://localhost:8086/state/auctions/$AUCTION_ID/highest-bid
# 기대: {"highestBid":200000,"highestBidderId":"bidder2-uuid","bidCount":2}
```

---

## Phase 7. Kafka 메시지 모니터링

**Kafka UI** (`http://localhost:9000`) 에서 GUI로 확인하거나 터미널에서:

```bash
# auction-events 토픽 소비
docker exec -it kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic auction-events \
  --from-beginning

# notification-events 모니터링 (OUTBID, AUCTION_WON, AUCTION_CLOSED 확인)
docker exec -it kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic notification-events \
  --from-beginning
```

> Avro 직렬화 형식이라 터미널에서는 raw byte로 보입니다.
> Kafka UI(9000)에서 Schema Registry 연동 시 가독성 있게 확인 가능합니다.

---

## Phase 8. 경매 마감 검증 (Punctuator)

`endsAt` 설정 시각이 지나면 **최대 30초 내**에 Punctuator가 발화합니다.

```bash
# 경매 상태 확인 (endsAt 경과 후 active=false 기대)
curl http://localhost:8086/state/auctions/$AUCTION_ID/status
# 기대: {"auctionId":"...","active":false,"endsAt":...}
```

Punctuator 발화 시 다음 3가지가 동시에 발행됩니다:
1. `notification-events` — `AUCTION_WON` (낙찰자)
2. `notification-events` — `AUCTION_CLOSED` (전체)
3. `auction-events` — `AUCTION_CLOSED`

---

## Phase 9. DLQ 확인

```bash
# 정상 처리 시 비어 있어야 함
docker exec -it kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic bid-dead-letter \
  --from-beginning \
  --max-messages 1
```

---

## 완료 체크리스트

| 단계 | 확인 명령 | 기대 결과 |
|------|---------|---------|
| 경매 생성 직후 | `GET /state/auctions/{id}/highest-bid` | `highestBid = startPrice(100000)` |
| 첫 입찰 후 | `GET /state/auctions/{id}/highest-bid` | `highestBid = 150000, bidCount = 1` |
| 두 번째 입찰 후 | `notification-events` 토픽 | `OUTBID` 메시지 있음 |
| endsAt 경과 ~30초 후 | `auction-events` 토픽 | `AUCTION_CLOSED` 있음 |
| endsAt 경과 ~30초 후 | `notification-events` 토픽 | `AUCTION_WON` + `AUCTION_CLOSED` 있음 |
| 전 구간 | `bid-dead-letter` 토픽 | 비어 있음 |

---

## 포트 참고

| 서비스 | 포트 |
|--------|------|
| API Gateway | 8080 |
| auction-streams | 8086 |
| Kafka | 9092 |
| Schema Registry | 8085 |
| Debezium REST | 8083 |
| Kafka UI | 9000 |
| PostgreSQL (auction) | 5432 |
| PostgreSQL (bid) | 5433 |
| PostgreSQL (user) | 5434 |
