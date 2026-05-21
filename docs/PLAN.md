# PLAN.md

이 문서는 프로젝트 초기 기획 단계에서 작성된 마일스톤별 설계 의도와 완료 기준을 기록합니다.
실제 진행 상태는 [GitHub Milestones](https://github.com/jaehoon9875/realtime-auction-service/milestones)에서 관리합니다.

---

## M1. 로컬 인프라 + 프로젝트 뼈대

- GitHub 설정 (CodeRabbit App 설치, Branch Protection Rules)
- Gradle 멀티모듈 세팅 (Kotlin DSL) — Spring Boot 4.0.6 / Java 21, user-service 등록
- docker-compose 작성 (Kafka, PostgreSQL, Redis, Debezium, Schema Registry) — `infra/docker-compose.yml`
- API Gateway 라우팅 기본 설정 — `services/api-gateway` (Spring Cloud Gateway 5.x, `spring-cloud-starter-gateway-server-webflux`)
- auction / bid / notification 서비스 Spring Boot 모듈은 **각 마일스톤(M3, M4, M6)에서 도메인 구현과 함께 생성**

**완료 기준**: `infra/`에서 `docker-compose up` 으로 인프라 기동 확인. API Gateway는 호스트에서 `./gradlew :services:api-gateway:bootRun` 후 라우팅·헬스 확인 (하위 서비스는 M2~ 단계에서 순차 기동)

---

## M2. User Service

- 회원가입 / 로그인 API
- JWT 발급 + Refresh Token Rotation
- API Gateway 인증 필터 연동

**완료 기준**: JWT로 인증된 요청이 Gateway 통과

---

## M3. Auction Service + Outbox + Debezium

- auction-service Spring Boot 모듈 생성·등록 (Gradle 멀티모듈)
- 경매 CRUD REST API
- Outbox Table 저장 (같은 트랜잭션)
- Debezium connector 설정
- Schema Registry Avro 스키마 등록 스크립트 작성 (현재 JsonConverter — AvroConverter 전환은 M5)
- Gateway 내부 시크릿 헤더 (X-Internal-Request-Token) 연결
- Gateway 인증 / 서비스 인가 역할 분리 (GatewayUserFilter)
- auction-events 토픽 발행 E2E 확인
- 단위·통합 테스트 (AuctionServiceTest, AuctionIntegrationTest)

**완료 기준**: 경매 생성 시 auction-events 토픽에 이벤트 적재 확인 (Avro 전환은 M5에서 결정)

---

## M4. Bid Service

- bid-service Spring Boot 모듈 생성·등록 (Gradle 멀티모듈)
- 입찰 REST API
- 유효성 검증 (State Store 조회)
- Resilience4j Circuit Breaker 적용
- Outbox + Debezium → bid-events 발행
- bid-outbox-connector.json 작성 (현재 JsonConverter — AvroConverter 전환은 M5)

**완료 기준**: 입찰 시 bid-events 토픽 적재 + Circuit Breaker 동작 확인 (Avro 전환은 M5에서 결정)

---

## M5. Kafka Streams App ⭐ 핵심

- State Store: 경매별 최고가 실시간 관리
- Windowed Aggregation: 입찰 급증 탐지
- Punctuator: 경매 마감 타이머
- notification-events 발행
- Dead Letter Queue 처리

**완료 기준**: 입찰 → State Store 갱신 + 마감 시 AUCTION_CLOSED 이벤트 발행

---

## M6. Notification Service + WebSocket

- notification-service Spring Boot 모듈 생성·등록 (Gradle 멀티모듈)
- notification-events 소비
- Redis 기반 WebSocket 세션 관리
- 클라이언트 실시간 push

**완료 기준**: 입찰 발생 시 WebSocket 클라이언트에 실시간 최고가 수신

---

## M7. 통합 테스트 + 문서화

- Testcontainers 통합 테스트
- E2E 시나리오 검증 (경매 생성 → 입찰 → 마감 → 낙찰 알림)
- OpenAPI/Swagger 자동 생성 전환 (진행 중)
  - ✅ `user-service`, `auction-service`, `bid-service`에 `springdoc-openapi-starter-webmvc-ui` 3.0.3
  - ✅ api-gateway 통합 Swagger UI + 각 서비스 `v3/api-docs` 프록시
  - ✅ 주요 엔드포인트 `@Operation`, `@ApiResponse`, `OpenApiConfig`(Bearer JWT)
  - ✅ `docs/api.md` → Swagger UI 링크 정본 + 레거시 수동 명세 보존
  - ⏸ `notification-service`: REST 없음(WebSocket 전용) — REST API 추가 시 springdoc 도입
- docs/ 문서 최종 정리
- README 최종 정리
- AI 워크플로우 도입 (선택) → [docs/ai-workflows.md](ai-workflows.md)
  - `ai-doc-update.yml` — main 머지 시 docs/ 자동 업데이트 PR 생성
  - `ai-issue-analysis.yml` — `analyze` 라벨 추가 시 이슈 원인/해결 방향 코멘트

**완료 기준**: 전체 시나리오 정상 동작

---

## M8. GKE 배포

- Terraform GKE 인프라 (cloud-sre-platform 연계)
- Kubernetes 매니페스트 작성
- ArgoCD GitOps 연동
- Strimzi Kafka Operator

**완료 기준**: GKE 위에서 전체 서비스 동작

---
