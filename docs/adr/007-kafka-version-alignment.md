---
status: "accepted"
date: 2026-05-22
decision-makers: jaehoon9875
---

# Kafka 버전 정렬 — Apache Kafka 4.1 / Confluent Platform 8.1 기준 통일

## Context and Problem Statement

프로젝트 내 Kafka 관련 버전이 세 곳에 분산되어 있으며, 초기 설정 시 정합성을 맞추지 않아 버전 불일치 상태가 발견되었습니다.

| 컴포넌트 | 변경 전 | 대응 Kafka 버전 |
|---|---|---|
| `cp-kafka` 브로커 (docker-compose) | 7.7.0 | Kafka 3.7 |
| Confluent 클라이언트 libs (build.gradle.kts) | 8.2.0 | Kafka 4.2 |
| Spring Boot 4.0.6 BOM → `kafka-clients`, `kafka-streams` | 4.1.2 | Kafka 4.1 |
| Spring Boot 4.0.6 BOM → `spring-kafka` | 4.0.5 | Kafka 4.x 지원 |

클라이언트(Kafka 4.1~4.2)가 브로커(Kafka 3.7)보다 메이저 버전이 높은 조합은 Kafka 공식 호환 정책 외 구간입니다.

**Kafka 브로커, 클라이언트 라이브러리, Confluent 컴포넌트를 어느 버전으로 통일해야 하는가?**

## Decision Drivers

- 브로커와 클라이언트 간 공식 지원 버전 조합 유지
- Spring Boot 4.0.6 BOM이 관리하는 `kafka-clients` 버전(4.1.2)에 맞추는 것이 오버라이드 없이 가장 단순
- M8 GKE 배포에서 Strimzi Kafka Operator 사용 예정 → Strimzi의 Kafka 4.1 지원 여부 고려
- "비교적 최신이면서 안정적인 버전" 목표

## Considered Options

- **CP 8.1 / Kafka 4.1로 전체 통일** (브로커를 올림)
- CP 7.7 / Kafka 3.7로 전체 통일 (클라이언트를 내림)

## Decision Outcome

Chosen option: **"CP 8.1 / Kafka 4.1로 전체 통일"**, because Spring Boot 4.0.6 BOM이 이미 `kafka-clients 4.1.2`를 관리하고 있어 BOM 오버라이드 없이 자연스럽게 맞출 수 있고, Strimzi도 Kafka 4.1을 지원하기 때문.

### 변경 대상

| 파일 | 변경 전 | 변경 후 |
|---|---|---|
| `infra/docker-compose.yml` — `cp-kafka` 이미지 | `7.7.0` | `8.1.0` |
| `infra/docker-compose.yml` — `cp-schema-registry` 이미지 | `7.7.0` | `8.1.0` |
| `streams/auction-streams/build.gradle.kts` — `confluentVersion` | `8.2.0` | `8.1.0` |
| `services/notification-service/build.gradle.kts` — `confluentVersion` | `8.2.0` | `8.1.0` |

`kafka-streams`, `kafka-clients`는 Spring Boot BOM(`4.1.2`)이 관리하므로 별도 변경 없음.

### Consequences

- Good, because 브로커·클라이언트·Schema Registry가 같은 Kafka 4.1 라인으로 정렬되어 공식 지원 구간 안에 들어옴
- Good, because Spring Boot BOM 오버라이드 없이 `kafka-clients 4.1.2`가 그대로 사용됨
- Good, because Confluent 8.1 → 8.2보다 한 단계 낮추어 클라이언트(4.1)와 브로커(4.1) 버전을 정확히 맞춤
- Bad, because docker-compose 브로커 이미지 교체 후 로컬 볼륨 초기화가 필요할 수 있음 (기존 kafka-data 볼륨 삭제 후 재기동)
- Bad, because Testcontainers에서 `confluentinc/cp-kafka:7.7.0`을 명시한 곳이 있다면 함께 변경 필요

### Confirmation

- `docker-compose up` 후 브로커 기동 확인
- Debezium, Schema Registry 정상 연동 확인
- `./gradlew :streams:auction-streams:test` 통과 확인
- 문제 없을 경우 status를 `accepted`로 변경

## Pros and Cons of the Options

### CP 8.1 / Kafka 4.1로 전체 통일 (브로커를 올림)

- Good, because Spring Boot BOM과 정렬되어 별도 버전 오버라이드 불필요
- Good, because 최신 안정 버전 목표에 부합
- Good, because Strimzi + Kafka 4.1 조합으로 M8 GKE 배포 시 일관성 유지
- Bad, because 로컬 docker-compose 볼륨 초기화 필요 (개발 데이터 유실)

### CP 7.7 / Kafka 3.7으로 전체 통일 (클라이언트를 내림)

- Good, because 기존 docker-compose 브로커 이미지 유지
- Bad, because Spring Boot BOM의 `kafka-clients 4.1.2`를 `3.7.x`로 강제 오버라이드 필요
- Bad, because Spring Boot 4.x에서 Kafka 3.x 클라이언트 강제는 역방향이며 미검증 구간
- Bad, because "최신 안정 버전" 목표와 역행
