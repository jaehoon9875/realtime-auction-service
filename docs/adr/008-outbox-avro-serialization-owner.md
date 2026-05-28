---
status: "accepted"
date: 2026-05-27
decision-makers: jaehoon9875
supersedes: ""
---

# Outbox payload Avro 직렬화 주체: 애플리케이션 + Debezium BinaryDataConverter pass-through

## Context and Problem Statement

M5 완료 후 Debezium `AvroConverter` + Outbox `JSONB payload` 조합으로 운영했으나,
Kafka Streams `SpecificAvroSerde<BidEvent>` 역직렬화 단계에서 wire format 불일치 오류가 발생했습니다.

**근본 원인:** Debezium `AvroConverter`는 JSONB 컬럼을 `io.debezium.data.Json` — 즉 **String 타입**으로 읽습니다.
이를 Avro로 직렬화하면 `{"type": "string"}` 스키마의 단순 문자열이 됩니다.
반면 Kafka Streams 쪽은 `BidEvent` Avro Record 스키마(`{"type": "record", ...}`)를 기대하므로 타입 불일치로 역직렬화가 항상 실패합니다.

```text
[기존 파이프라인]
앱 → outbox_events.payload (JSONB 문자열)
Debezium AvroConverter → Avro string 타입 메시지
Kafka Streams SpecificAvroSerde<BidEvent> → ❌ Record 타입 기대, String 수신 → 역직렬화 실패
```

**결정 질문:** Outbox payload를 Avro로 직렬화할 책임을 누가 가져야 하는가?

## Decision Drivers

- Kafka Streams `SpecificAvroSerde`와 wire format이 정확히 일치해야 함
- ADR-004에서 결정한 "Avro + Schema Registry" 원칙은 유지
- Debezium은 CDC 인프라이므로 비즈니스 직렬화 로직 없이 단순하게 유지
- 로컬/CI(Testcontainers) 환경에서도 동일한 파이프라인 동작 보장

## Considered Options

- **Option A: 애플리케이션이 Avro 직렬화 + Debezium BinaryDataConverter (pass-through)**
- Option B: Debezium `expand.json.payload=true` + Avro Record 변환

## Decision Outcome

**Chosen option: Option A** — 애플리케이션이 Outbox에 저장할 때 **Confluent Avro wire format**(magic byte 0x00 + 4바이트 schema id + Avro 바이너리)으로 직렬화하여 DB에 **BYTEA**로 저장하고, Debezium은 `BinaryDataConverter`로 **그대로 Kafka에 전달(pass-through)**한다.

```text
[변경 후 파이프라인]
앱 (KafkaAvroSerializer) → outbox_events.payload (BYTEA, Confluent Avro wire format)
Debezium BinaryDataConverter → Kafka 토픽 (바이트 그대로 pass-through)
Kafka Streams SpecificAvroSerde<BidEvent> → ✅ wire format 일치, 정상 역직렬화
```

### Consequences

- Good, because 앱·Streams·Schema Registry가 동일한 Avro 스키마를 공유하므로 wire format이 구조적으로 일치함
- Good, because Debezium이 직렬화 로직에 관여하지 않아 CDC 인프라가 단순해짐
- Good, because Schema Registry 스키마 버전 관리(ADR-004)는 그대로 유지됨
- Bad, because 앱 코드에 `KafkaAvroSerializer` 의존성이 추가됨
- Bad, because Outbox payload가 사람이 직접 읽을 수 없는 BYTEA가 되어 DB에서 직접 디버깅이 어려워짐
- Bad, because 로컬 환경에서도 Schema Registry가 기동되어 있어야 앱이 Outbox를 저장할 수 있음
- Bad, because 기존 JSONB payload에서 BYTEA로 전환하는 Flyway 마이그레이션(V8: auction, V4: bid) 필요

### Confirmation

Debezium 커넥터 `value.converter=io.debezium.converters.BinaryDataConverter` 설정,
auction-outbox-connector / bid-outbox-connector 모두 connector=RUNNING / task=RUNNING 확인.
GKE E2E Smoke Test PASS 13 / FAIL 0 달성 (2026-05-27).

## Pros and Cons of the Options

### Option A: 애플리케이션 Avro 직렬화 + BinaryDataConverter (채택)

- Good, because 직렬화 책임이 애플리케이션 코드 안에 있어 Schema Registry와의 계약이 컴파일 타임에 검증됨
- Good, because Debezium은 byte 배열을 그대로 통과시키므로 wire format 변환 오류 가능성 없음
- Good, because Streams `SpecificAvroSerde`와 동일한 `KafkaAvroSerializer`가 사용되므로 schema id가 일치함
- Bad, because 앱 실행 시 Schema Registry 접근이 필요함 (Outbox payload 직렬화)
- Bad, because Avro Java 클래스 생성을 위해 빌드 시 `infra/avro` 스키마 파일 참조가 필요함
- Bad, because Outbox payload가 BYTEA이므로 DB 직접 조회 시 가독성 없음

### Option B: Debezium expand.json.payload + Avro Record 변환

- Good, because 앱 코드 변경이 없고 Outbox payload는 기존 JSONB 유지 가능
- Bad, because `expand.json.payload=true` 는 JSONB를 Avro 동적 Record로 변환하는 방식이라 Streams의 `SpecificAvroSerde`와 schema id가 달라 여전히 불일치 발생 가능
- Bad, because Debezium 내부의 JSON→Avro 변환 로직에 비즈니스 스키마 정보가 암묵적으로 결합됨

## 관련 문서

- [ADR-004: Avro + Schema Registry](004-avro-schema-registry.md) — 직렬화 포맷(Avro) 결정은 유지됨. 이 ADR은 직렬화 **주체(앱 vs Debezium)** 결정을 추가함
- [docs/debezium-connector.md](../debezium-connector.md) — BinaryDataConverter 설정 상세
- [docs/schema.md](../schema.md) — outbox_events.payload BYTEA 스키마
- [docs/avro-schema.md](../avro-schema.md) — Schema Registry 등록 절차
