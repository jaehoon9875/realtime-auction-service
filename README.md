# 🔨 Realtime Auction Service

> Kafka Streams 기반 실시간 경매 서비스

## Overview

입찰 이벤트를 Kafka Streams로 실시간 처리하는 MSA 기반 경매 플랫폼입니다.

Kafka Streams의 상태 기반 처리(State Store), Debezium CDC를 활용한 Outbox Pattern, WebSocket 기반 실시간 알림까지 분산 시스템의 핵심 문제들을 다룹니다.

## Architecture

```
[Client]
   │
   ├─ REST API
   └─ WebSocket (/ws/auctions/{id}, /ws/users/me)
          │
   [API Gateway] (Spring Cloud Gateway)
          │
   ┌──────┼──────────────────┐
   │      │                  │
[Auction] [Bid]         [User]     [Notification]
[Service] [Service]    [Service]   [Service]
   │         │                          ▲
   └────┬────┘                          │
   Outbox Table                         │
        │                               │
   [Debezium CDC]                       │
        │                               │
      [Kafka]                           │
   auction-events                       │
   bid-events ──▶ [Kafka Streams] ──────┘
                   - State Store (최고가 관리)
                   - Windowed Aggregation (이상 탐지)
                   - Punctuator (마감 처리)
                        │
                  notification-events
```

## Key Technical Decisions

### 1. Outbox Pattern + Debezium CDC
Auction/Bid Service가 DB 저장과 동시에 Kafka에 직접 발행하면, 그 사이 장애 시 이벤트가 유실됩니다.
Outbox Table에 같은 트랜잭션으로 저장하고 Debezium이 WAL을 읽어 발행함으로써 **이벤트 유실 없는 발행을 보장**합니다.

### 2. Kafka Streams State Store
입찰이 동시에 몰릴 때 DB UPDATE 경합을 피하기 위해 현재 최고가를 DB가 아닌 **Kafka Streams 내장 State Store(RocksDB)** 에서 관리합니다.
`GET /auctions/{id}` 의 currentPrice는 DB가 아닌 State Store에서 조회합니다.

### 3. Redis 기반 WebSocket 세션 공유
Notification Service가 여러 인스턴스로 스케일아웃될 때, 특정 사용자의 WebSocket 세션이 어느 인스턴스에 연결되어 있는지 **Redis에 공유**하여 멀티 인스턴스 환경에서도 알림을 정확하게 전달합니다.

### 4. Schema Registry + Avro
서비스 간 Kafka 이벤트 스키마 변경 시 하위 호환성을 보장하기 위해 Confluent Schema Registry와 Avro를 사용합니다. 스키마 버전 관리로 서비스 간 계약을 명시적으로 관리합니다.

### 5. Circuit Breaker (Resilience4j)
Bid Service가 Auction Service에 경매 정보를 조회할 때, Auction Service 장애가 Bid Service로 전파되지 않도록 Circuit Breaker를 적용합니다.

## Services

| Service | 역할 | Port |
|---|---|---|
| **API Gateway** | 라우팅, 인증 필터 | 8080 |
| **Auction Service** | 경매 CRUD, Outbox 발행 | 8081 |
| **Bid Service** | 입찰 처리, 유효성 검증 | 8082 |
| **User Service** | 회원가입/로그인, JWT | 8083 |
| **Notification Service** | Kafka 소비 → WebSocket push | 8084 |
| **Auction Streams** | 실시간 집계, 마감 처리 | - |

## Tech Stack

### Backend
- Java 21, Spring Boot 4
- Spring Cloud Gateway
- Spring Security + JWT (Refresh Token Rotation)
- Kafka Streams
- Resilience4j

### Data
- PostgreSQL (서비스별 독립 DB)
- Redis (WebSocket 세션 공유)
- Apache Kafka + Schema Registry + Avro
- Debezium (CDC)

### Infra
- GKE (Google Kubernetes Engine)
- Terraform
- Strimzi Kafka Operator
- ArgoCD (GitOps)
- Prometheus + Grafana

### Test
- Testcontainers (Kafka, PostgreSQL, Redis 통합 테스트)

## Documentation

- [API 명세](docs/api.md)
- [DB 스키마](docs/schema.md)
- [Kafka 토픽 설계](docs/kafka.md)
- [아키텍처 상세](docs/architecture.md)

## Project Structure

```text
realtime-auction-service/
├── services/
│   ├── auction-service/
│   ├── bid-service/
│   ├── user-service/
│   └── notification-service/
├── streams/
│   └── auction-streams/
├── infra/
│   ├── k8s/
│   ├── terraform/
│   └── docker-compose.yml
├── docs/
└── README.md
```

## Local Development

```bash
# 인프라 실행 (Kafka, PostgreSQL, Redis, Debezium)
docker-compose up -d

# 각 서비스 실행
./gradlew :services:auction-service:bootRun
./gradlew :services:bid-service:bootRun
./gradlew :services:user-service:bootRun
./gradlew :services:notification-service:bootRun
./gradlew :streams:auction-streams:bootRun
```

## Related Projects

- [observability-platform](https://github.com/jaehoon9875/observability-platform) - Kubernetes 기반 Observability 스택 (Prometheus, Grafana, Loki, Tempo)
- [cloud-sre-platform](https://github.com/jaehoon9875/cloud-sre-platform) - GCP 기반 SRE 플랫폼 (GKE, Terraform, FinOps)
