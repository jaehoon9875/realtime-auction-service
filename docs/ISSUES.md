# ISSUES.md

이슈 추적은 [GitHub Issues](https://github.com/jaehoon9875/realtime-auction-service/issues)에서 관리합니다.
이 파일은 M1~M5 기간의 해결된 이슈 아카이브만 보관합니다.

---

## 해결된 이슈 아카이브 (M1~M5)

| # | 심각도 | 마일스톤 | 제목 | 해결일 | 해결 방법 |
|---|--------|---------|------|--------|-----------|
| 7 | 🟡 중간 | M5 · streams/docs | State Store 초기 `currentPrice` 정책 부재 | 2026-05-07 | M5 구현 정책을 확정해 `docs/kafka.md`에 반영. `AUCTION_CREATED.startPrice`를 State Store 초기 `currentPrice` 시드로 사용하고, 입찰 0건 경매도 조회값이 시작가와 일치하도록 명시. 마감 Punctuator 기준은 `WALL_CLOCK_TIME`으로 확정. |
| 6 | 🟡 중간 | M5 · infra/docs | Avro/Json 컨버터 불일치 | 2026-05-07 → **2026-05-27 재수정** | **M5 1차 수정**: `value.converter=AvroConverter`, `expand.json.payload=true`로 통일. **GKE E2E 검증 중 재발**: Debezium `AvroConverter`가 JSONB 컬럼을 `io.debezium.data.Json`(String 타입)으로 읽어 Avro string으로 직렬화 → Kafka Streams `SpecificAvroSerde<BidEvent>`가 Record 타입 기대하여 역직렬화 실패. **2차 수정(ADR-008)**: 직렬화 책임을 애플리케이션으로 이동. 앱이 `KafkaAvroSerializer`로 Confluent Avro wire format을 BYTEA로 Outbox 저장, Debezium은 `BinaryDataConverter`로 pass-through. Flyway V8(auction)/V4(bid) BYTEA 마이그레이션 + V9(auction)/V5(bid) `GRANT SELECT ON outbox_events TO debezium` 추가. |
| 2 | 🟡 중간 | docs · auction-service | 마감 후 `auctions.status` 동기화 | 2026-05-05 | **역할 분리**: DB **`CLOSED`는 Auction Service** (`endsAt` 경과 시 스케줄러·명시적 전이). Streams **`AUCTION_CLOSED`/notification** 은 알림·실시간용. 입찰 **`endsAt` + 상태** 검증. 문서: `docs/architecture.md`, `docs/kafka.md`, `services/CLAUDE.md`. **구현**: `AuctionEndScheduler`·`AuctionService.closeOverdueAuctions()`, `findIdsOngoingPastEnd`, 설정 `app.auction.schedule.ongoing-to-closed-ms`(기본 60초), Outbox `AUCTION_STATUS_CHANGED`. |
| 5 | 🟢 낮음 | infra | CI 트리거 중복 | 2026-05-05 | `push: branches`에서 `feature/**`, `fix/**` 제거. PR 브랜치는 `pull_request` 트리거만으로 커버. |
| 1 | 🟡 중간 | auction-service | 경매 상태 정합 + `startsAt`·PENDING→ONGOING (구 보류 이슈 1) | 2026-05-05 | **용어·종료**: 코드·DB 진행 중 상태 **`ONGOING`** 통일, 종료는 **`CLOSED`** 만 (`CANCELLED`/`AUCTION_CANCELLED` 문서·설명 정리). Flyway **`V4`** CHECK·`ACTIVE`→`ONGOING` 마이그레이션. **시작·전환**: **`starts_at`** 컬럼·API **`startsAt`** (생략 시 생성 시각), 생성 시 `startsAt > now` → **`PENDING`**, 그 외 **`ONGOING`**. **`AuctionStartScheduler`**·`activateDueAuctions()` 로 예약 경매 시작 시 **`AUCTION_STATUS_CHANGED`** Outbox. Outbox/Avro에 **`startsAt`** epoch, `docs/schema.md`·`api.md`·`kafka.md`·`CLAUDE.md` 동기화. 입찰 시점 검증(Bid Service)은 후속. |

