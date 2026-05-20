# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

---

## [0.6.0] - 2026-05-20 — M6: Notification Service + WebSocket

### Added
- notification-service Spring Boot 모듈 (WebFlux, 포트 8084)
- `notification-events` Kafka Consumer → WebSocket 실시간 push
- Raw WebSocket 엔드포인트: `/ws/auctions/{auctionId}`, `/ws/users/me`
- Redis 기반 세션 공유 및 멀티 인스턴스 Pub/Sub 라우팅 (`notify:{instanceId}`)
- WebFlux JWT Resource Server (`?token=` 쿼리 파라미터 지원)
- auction-streams `BID_REJECTED` → `notification-events` 분기
- docker-compose `notification-service` 컨테이너 및 Dockerfile
- 단위·통합 테스트 (`integrationTest` 태스크, Docker 필요)

---

## [0.5.0] - 2026-05-10 — M5: Kafka Streams App

### Added
- Kafka Streams 토폴로지 구현 (경매별 최고가 State Store 실시간 관리)
- Windowed Aggregation 기반 입찰 급증 탐지
- Punctuator 기반 경매 마감 타이머
- notification-events 토픽 발행
- Dead Letter Queue 처리

---

## [0.4.0] - 2026-05-06 — M4: Bid Service

### Added
- bid-service Spring Boot 모듈
- 입찰 REST API
- State Store 기반 입찰 유효성 검증 (최고가 확인)
- Resilience4j Circuit Breaker 적용
- Outbox + Debezium CDC → bid-events 발행

---

## [0.3.0] - 2026-05-04 — M3: Auction Service + Outbox + Debezium

### Added
- auction-service Spring Boot 모듈
- 경매 CRUD REST API
- Outbox Pattern (Outbox Table 동일 트랜잭션 저장)
- Debezium CDC 커넥터 설정
- Schema Registry Avro 스키마 등록 스크립트
- API Gateway 내부 서비스 인증 헤더 (X-Internal-Request-Token)
- Gateway 인증/인가 역할 분리 (GatewayUserFilter)

---

## [0.2.0] - 2026-05-02 — M2: User Service

### Added
- 회원가입 / 로그인 REST API
- JWT RSA 기반 인증 + Refresh Token Rotation (Redis)
- API Gateway JWT 인증 필터 연동

---

## [0.1.0] - 2026-04-30 — M1: 로컬 인프라 + 프로젝트 뼈대

### Added
- Gradle 멀티모듈 프로젝트 구성 (Kotlin DSL, Spring Boot 4 / Java 21)
- docker-compose 로컬 인프라 (Kafka, PostgreSQL, Redis, Debezium, Schema Registry)
- API Gateway 기본 라우팅 설정 (Spring Cloud Gateway)
- GitHub Actions CI 워크플로우
- CodeRabbit 코드 리뷰 설정
