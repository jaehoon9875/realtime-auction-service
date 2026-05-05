# Schema Registry / Avro 등록 가이드

Kafka 메시지 값의 **계약(필드·타입)** 을 Avro로 정의하고, Confluent Schema Registry에 올려 두면
프로듀서·컨슈머가 동일한 스키마로 직렬화·역직렬화할 수 있고, 스키마 버전 호환성 검사도 할 수 있습니다.

---

## 이 프로젝트에서의 위치


| 항목               | 설명                                                                     |
| ---------------- | ---------------------------------------------------------------------- |
| 스키마 원본           | `infra/avro/*.avsc` (Git으로 버전 관리)                                      |
| 도메인 문서와의 관계      | 필드 정의는 [docs/kafka.md](./kafka.md)와 **반드시 동기화**                        |
| 등록 스크립트          | `infra/avro/register-schemas.sh`                                       |
| Registry 주소 (로컬) | `http://localhost:${SCHEMA_REGISTRY_PORT}` (기본 **8085**, `infra/.env`) |


> **M3 현재:** Debezium `auction-outbox-connector`는 토픽 값을 **JsonConverter**로 발행하도록 설정되어 있습니다.
> Registry에 Avro를 올려 두는 것은 **계약 확정 + M5 이후 AvroConverter 전환**을 위한 준비 단계입니다.

---

## 사전 조건

1. `docker-compose`로 **schema-registry** 컨테이너가 기동된 상태
2. 호스트에 `jq`, `curl` 설치
3. (선택) Registry만 테스트할 때는 Kafka 토픽 생성과 무관하게 등록 가능

---

## 등록 절차

```bash
cd infra/avro
./register-schemas.sh
```

성공 시 각 subject에 대해 JSON 응답에 `"id": <버전에 해당하는 전역 스키마 id>` 가 포함됩니다.

---

## 확인 명령

```bash
# 등록된 subject 목록
curl -sS "${SCHEMA_REGISTRY_URL:-http://localhost:8085}/subjects"

# auction-events 최신 스키마
curl -sS "${SCHEMA_REGISTRY_URL:-http://localhost:8085}/subjects/auction-events-value/versions/latest"
```

---

## Subject 명명 규칙

Confluent 관례: 토픽 `auction-events`의 **값** 스키마 → subject 이름 `**auction-events-value`**.

---

## 스키마 변경 시

1. `infra/avro/AuctionEvent.avsc` 수정
2. [docs/kafka.md](./kafka.md) 의 해당 레코드 블록 동기화
3. `./register-schemas.sh` 재실행 → Registry에 **새 버전** 등록 (호환 규칙은 Registry 설정에 따름)

현재 내용과 동일한 스키마를 다시 등록하면 기존 id를 재사용하고 새 버전이 생기지 않을 수 있습니다.