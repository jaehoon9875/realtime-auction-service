---
status: "accepted"
date: 2026-05-18
decision-makers: jaehoon9875
---

# Avro + Schema Registry로 이벤트 스키마 관리

## Context and Problem Statement

여러 서비스(Auction, Bid, Notification, Streams)가 Kafka를 통해 이벤트를 교환합니다.
스키마 정의 없이 JSON을 사용하면 서비스 간 이벤트 계약이 암묵적이 되어, 스키마 변경 시 consumer 장애가 발생해도 사전에 감지하기 어렵습니다.

**서비스 간 Kafka 이벤트의 스키마 버전을 명시적으로 관리하고 하위 호환성을 보장하려면 어떤 직렬화 방식을 선택해야 하는가?**

## Decision Drivers

- 스키마 변경 시 기존 consumer 장애 없이 하위 호환성 보장
- 서비스 간 이벤트 계약을 코드가 아닌 명시적 스키마로 관리
- producer/consumer 간 스키마 버전 불일치를 런타임 이전에 감지

## Considered Options

- Avro + Confluent Schema Registry
- JSON (스키마 미관리)
- Protocol Buffers (Protobuf)

## Decision Outcome

Chosen option: **"Avro + Confluent Schema Registry"**, because Schema Registry의 호환성 검사(BACKWARD/FORWARD)를 통해 스키마 변경 시 사전에 불일치를 감지할 수 있고, Kafka 생태계에서 Avro + Schema Registry가 사실상 표준(de facto standard)으로 가장 넓은 지원을 받기 때문.

### Consequences

- Good, because Schema Registry가 스키마 버전을 중앙 관리하여 producer/consumer 간 계약이 명시적으로 유지됨
- Good, because Avro 바이너리 직렬화로 JSON 대비 페이로드 크기 감소 및 직렬화 성능 향상
- Good, because BACKWARD 호환성 정책으로 새 필드 추가 시 기존 consumer 영향 없음
- Bad, because Schema Registry 추가 인프라 운영 필요
- Bad, because Avro 바이너리는 사람이 직접 읽을 수 없어 디버깅 시 불편함
- Bad, because Avro 스키마 파일(`.avsc`) 관리 및 등록 절차가 필요 ([avro-schema.md](../avro-schema.md) 참고)

### Confirmation

Schema Registry에 스키마가 정상 등록되고, producer가 Avro로 직렬화한 메시지를 consumer가 역직렬화하여 정상 소비함을 확인.
관련 문서: [avro-schema.md](../avro-schema.md), [kafka.md](../kafka.md)

## Pros and Cons of the Options

### Avro + Confluent Schema Registry

- Good, because Kafka 생태계에서 가장 광범위하게 지원되는 조합
- Good, because 바이너리 직렬화로 JSON 대비 페이로드 크기 감소
- Good, because Schema Registry의 호환성 검사로 스키마 변경 사고를 사전 차단
- Bad, because Schema Registry 운영 부담 및 로컬 환경에서도 실행 필요
- Bad, because 별도 `.avsc` 스키마 파일 관리 필요

### JSON (스키마 미관리)

- Good, because 별도 인프라 없이 바로 사용 가능, 구현이 단순
- Good, because 사람이 직접 읽고 디버깅 가능
- Bad, because 스키마 변경 시 producer/consumer 간 계약이 암묵적이어서 장애 감지 어려움
- Bad, because 필드 타입 불일치, 필드 누락 등의 오류가 런타임에야 발견됨
- Bad, because JSON 페이로드 크기가 Avro 대비 크고 직렬화 비용이 높음

### Protocol Buffers (Protobuf)

- Good, because 바이너리 직렬화로 성능 우수, 강타입 스키마 정의
- Good, because gRPC와의 통합이 자연스러움
- Neutral, because Kafka와의 조합 시 별도 직렬화 설정 필요 (Schema Registry는 Avro/JSON Schema 중심)
- Bad, because Kafka 생태계에서 Avro 대비 지원이 적고, Confluent Schema Registry와의 통합이 추가 설정 필요
