# M5 Kafka Streams App — 세부 구현 체크리스트

> 새 챗 세션 인계용 임시 파일. 완료 후 삭제.

---

## 사전 확인 사항

- `settings.gradle.kts`에 `streams:auction-streams` 미등록 → Step 2에서 추가
- `streams/auction-streams/CLAUDE.md`에 Punctuator 타입이 `STREAM_TIME`으로 기재되어 있으나
  ISSUES.md 이슈 #7에서 `WALL_CLOCK_TIME`으로 확정 → Step 1에서 즉시 수정
- State Store 초기 `currentPrice` 시드 정책: `AUCTION_CREATED.startPrice` 사용 (이슈 #7 확정)
- `notification-events`는 Streams가 직접 발행 (Outbox 아님). exactly-once 보장으로 Outbox 불필요.

---

## Step 1. CLAUDE.md 불일치 수정

- [ ] `streams/auction-streams/CLAUDE.md` — 핵심 설계 주의사항 항목 수정
  - 변경 전: `Punctuator는 stream time 기반(PunctuationType.STREAM_TIME)으로 동작한다.`
  - 변경 후: `Punctuator는 wall clock time 기반(PunctuationType.WALL_CLOCK_TIME)으로 동작한다.`

---

## Step 2. Gradle 모듈 뼈대

- [ ] `settings.gradle.kts`에 `include("streams:auction-streams")` 추가
- [ ] `streams/auction-streams/build.gradle.kts` 작성
  - Spring Boot Web + Actuator
  - `kafka-streams`, `spring-kafka`
  - `kafka-avro-serializer` + `kafka-streams-avro-serde` (Confluent)
  - Schema Registry 클라이언트 (`confluent-schema-registry-client`)
- [ ] `streams/auction-streams/src/main/resources/application.yml` 작성
  ```yaml
  spring.kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
  streams:
    application-id: auction-streams
    properties:
      processing.guarantee: exactly_once_v2
      schema.registry.url: ${SCHEMA_REGISTRY_URL:http://localhost:8081}
      default.key.serde: org.apache.kafka.common.serialization.Serdes$StringSerde
      state.dir: /tmp/kafka-streams/auction-streams
  ```
- [ ] `AuctionStreamsApplication.java` main class 생성

---

## Step 3. Avro Serde + 도메인 모델

### State Store value 객체

- [x] `AuctionBidState.java` — `auction-highest-bid` Store용
  - 필드: `long highestBid`, `String highestBidderId`, `int bidCount`
  - 초기값: `highestBid = startPrice`, `bidCount = 0`

- [x] `AuctionMetadata.java` — `auction-metadata` Store용
  - 필드: `long endsAt`, `long startPrice`, `String sellerId`, `String title`

### Serde 설정

- [x] `StreamsSerdeConfig.java` — `@Configuration`
  - `KafkaAvroSerializer` / `KafkaAvroDeserializer` 기반 Serde Bean 등록
  - Schema Registry URL 환경변수로 주입
  - `AuctionBidState` / `AuctionMetadata`용 JSON Serde (State Store 내부 직렬화는 Jackson 사용 가능)

---

## Step 4. Kafka Streams 토폴로지

### 4-1. bid-events → auction-highest-bid State Store

- [ ] `BidStreamsTopology.java` — `@Component`

처리 흐름:
```
KStream<String, BidEvent> bidStream = builder.stream("bid-events")
  → filter: eventType == "BID_PLACED"
  → selectKey: record.auctionId
  → groupByKey
  → aggregate(
      initializer: auction-metadata에서 startPrice 조회 → AuctionBidState 초기화,
      aggregator: amount > highestBid이면 갱신 (이전 highestBidderId 보존 후 OUTBID 발행용으로 사용),
      store: auction-highest-bid (persistent)
    )
  → toStream
  → flatMap: OUTBID 이벤트 생성 (이전 최고 입찰자가 있는 경우)
  → to: notification-events
```

주의:
- `aggregate`의 `initializer`에서 `auction-metadata` Store를 직접 조회해 `startPrice`를 초기값으로 사용
- OUTBID 알림은 이전 `highestBidderId`가 존재하고 새 입찰자와 다를 때만 발행
- DLQ: try-catch 내에서 실패 시 `bid-dead-letter` 토픽으로 라우팅

### 4-2. auction-events → auction-metadata Store + Punctuator 등록

- [ ] `AuctionStreamsTopology.java` — `@Component`

처리 흐름:
```
KStream<String, AuctionEvent> auctionStream = builder.stream("auction-events")
  → filter: eventType == "AUCTION_CREATED"
  → process(AuctionMetadataProcessor::new, "auction-metadata")
```

- [ ] `AuctionMetadataProcessor.java` — `Processor<String, AuctionEvent, Void, Void>`
  - `init()`: `context.schedule(Duration.ofSeconds(30), WALL_CLOCK_TIME, this::checkExpiredAuctions)` 등록
  - `process()`: `auction-metadata` Store에 `{endsAt, startPrice, sellerId}` 저장
  - `checkExpiredAuctions()` (Punctuator):
    ```
    auction-metadata Store 전체 순회 (KeyValueIterator)
      → endsAt < now 인 경매:
          → auction-highest-bid Store 조회 (낙찰자 확정)
          → notification-events 발행: AUCTION_WON (낙찰자), AUCTION_CLOSED (전체)
          → auction-events 발행: AUCTION_CLOSED
          → auction-metadata에서 해당 auctionId 삭제 (중복 발행 방지)
    ```

### 4-3. DLQ 처리

- [ ] `DlqExceptionHandler.java` — `implements DeserializationExceptionHandler`
  - 역직렬화 실패 이벤트 → `bid-dead-letter` 또는 `auction-dead-letter` 토픽으로 발행
  - 원본 raw bytes + 에러 메시지 + `failedAt` 포함

- [ ] `StreamsExceptionHandler.java` — `implements StreamsUncaughtExceptionHandler`
  - 처리 중 uncaught exception → `REPLACE_THREAD` 반환 (스레드 재시작)
  - 반복 실패 임계치 초과 시 DLQ 라우팅

- [ ] `application.yml`에 핸들러 등록
  ```yaml
  spring.kafka.streams.properties:
    default.deserialization.exception.handler: com.jaehoon.streams.exception.DlqExceptionHandler
  ```

---

## Step 5. Interactive Queries REST API

- [ ] `StateQueryController.java` — `@RestController`

```java
// GET /state/auctions/{auctionId}/highest-bid
// GET /state/auctions/{auctionId}/status
```

- [ ] **단일·멀티 동일 구현 (멀티 전제)**: 스케일 아웃을 기본으로 하고 코드 경로는 한 가지만 둔다.
  - `KafkaStreams#queryMetadataForKey` + `KeyQueryMetadata#activeHost()` 로 해당 키의 담당 인스턴스(`application.server`)를 구한다. (Kafka 4.x — 예전 `streamsMetadataForKey` 대체)
  - 담당이 **이 JVM**이면 `kafkaStreams.store(StoreQueryParameters.fromNameAndType(...))` 로 `ReadOnlyKeyValueStore#get`
  - 담당이 **다른 인스턴스**이면 동일 REST 경로를 peer HTTP로 위임
  - 인스턴스가 1대뿐이면 activeHost는 항상 자기 자신 → 위임 없이 로컬 조회만 수행

응답 형식:
```json
// highest-bid
{ "auctionId": "...", "highestBid": 10000, "highestBidderId": "...", "bidCount": 3 }

// status
{ "auctionId": "...", "active": true, "endsAt": 1234567890000 }
```

---

## Step 6. bid-service 연동 확인

- [ ] bid-service 코드에서 최고가 조회 엔드포인트 확인
  - `GET /state/auctions/{id}/highest-bid` (auction-streams)를 바라보는지 검증
  - 호스트/포트 환경변수가 `application.yml`에 올바르게 설정되어 있는지 확인
- [ ] Circuit Breaker가 auction-streams 다운 시에도 동작하는지 확인 (Resilience4j)

---

## Step 7. E2E 검증

순서대로 확인:

- [ ] `docker-compose up` 으로 Kafka, Schema Registry, Debezium 기동
- [ ] Avro 스키마 등록: `infra/avro/register-schemas.sh` 실행
- [ ] Debezium 커넥터 등록: `infra/scripts/register-connectors.sh` 실행
- [ ] `./gradlew :streams:auction-streams:bootRun` 기동
- [ ] 경매 생성 API 호출 → `auction-events` 토픽 메시지 확인 (Avro)
- [ ] `GET /state/auctions/{id}/highest-bid` → startPrice가 초기 highestBid로 반환되는지 확인
- [ ] 입찰 API 호출 → `bid-events` 토픽 확인 → State Store 갱신 확인
- [ ] `endsAt` 경과 후 30초 이내 Punctuator 발화 확인
  - `notification-events`: `AUCTION_CLOSED` + `AUCTION_WON` 메시지 확인
  - `auction-events`: `AUCTION_CLOSED` 메시지 확인

---

## 완료 기준 (PLAN.md 기준)

> 입찰 → State Store 갱신 + 마감 시 AUCTION_CLOSED 이벤트 발행

- [ ] `bid-events` 소비 후 `GET /state/auctions/{id}/highest-bid` 값이 실시간 갱신됨
- [ ] `endsAt` 경과 경매에 대해 `auction-events` 토픽에 `AUCTION_CLOSED` 발행 확인
- [ ] `notification-events` 토픽에 `AUCTION_WON`, `AUCTION_CLOSED` 발행 확인
- [ ] 처리 실패 이벤트가 DLQ 토픽으로 이동 확인

---

## 관련 문서

- [docs/kafka.md](kafka.md) — 토픽·이벤트 스키마 상세
- [docs/architecture.md](architecture.md) — 전체 흐름 다이어그램, 경매 마감 정책
- [streams/auction-streams/CLAUDE.md](../streams/auction-streams/CLAUDE.md) — 모듈 설계 문맥
- [docs/ISSUES.md](ISSUES.md) — 이슈 #7 (WALL_CLOCK_TIME 확정), 이슈 #4 (BID_REJECTED 미결)
