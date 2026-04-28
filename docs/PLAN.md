# PLAN.md

## 마일스톤 진행 현황

| 마일스톤 | 내용 | 상태 |
|---------|------|------|
| M1 | 로컬 인프라 + 프로젝트 뼈대 | ⬜ 진행 전 |
| M2 | User Service | ⬜ 진행 전 |
| M3 | Auction Service + Outbox + Debezium | ⬜ 진행 전 |
| M4 | Bid Service | ⬜ 진행 전 |
| M5 | Kafka Streams App | ⬜ 진행 전 |
| M6 | Notification Service + WebSocket | ⬜ 진행 전 |
| M7 | 통합 테스트 + 문서화 | ⬜ 진행 전 |
| M8 | GKE 배포 | ⬜ 진행 전 |

---

## M1. 로컬 인프라 + 프로젝트 뼈대

- [ ] GitHub 설정 (CodeRabbit App 설치, Branch Protection Rules)
- [ ] Gradle 멀티모듈 세팅 (Kotlin DSL)
- [ ] docker-compose 작성 (Kafka, PostgreSQL, Redis, Debezium, Schema Registry)
- [ ] 각 서비스 Spring Boot 프로젝트 생성
- [ ] API Gateway 라우팅 기본 설정

**완료 기준**: `docker-compose up` 후 각 서비스가 뜨고 Gateway 라우팅 확인

---

## M2. User Service

- [ ] 회원가입 / 로그인 API
- [ ] JWT 발급 + Refresh Token Rotation
- [ ] API Gateway 인증 필터 연동

**완료 기준**: JWT로 인증된 요청이 Gateway 통과

---

## M3. Auction Service + Outbox + Debezium

- [ ] 경매 CRUD REST API
- [ ] Outbox Table 저장 (같은 트랜잭션)
- [ ] Debezium connector 설정
- [ ] Schema Registry + Avro 스키마 등록
- [ ] auction-events 토픽 발행 확인

**완료 기준**: 경매 생성 시 auction-events 토픽에 Avro 이벤트 적재 확인

---

## M4. Bid Service

- [ ] 입찰 REST API
- [ ] 유효성 검증 (State Store 조회)
- [ ] Resilience4j Circuit Breaker 적용
- [ ] Outbox + Debezium → bid-events 발행

**완료 기준**: 입찰 시 bid-events 토픽 적재 + Circuit Breaker 동작 확인

---

## M5. Kafka Streams App ⭐ 핵심

- [ ] State Store: 경매별 최고가 실시간 관리
- [ ] Windowed Aggregation: 입찰 급증 탐지
- [ ] Punctuator: 경매 마감 타이머
- [ ] notification-events 발행
- [ ] Dead Letter Queue 처리

**완료 기준**: 입찰 → State Store 갱신 + 마감 시 AUCTION_CLOSED 이벤트 발행

---

## M6. Notification Service + WebSocket

- [ ] notification-events 소비
- [ ] Redis 기반 WebSocket 세션 관리
- [ ] 클라이언트 실시간 push

**완료 기준**: 입찰 발생 시 WebSocket 클라이언트에 실시간 최고가 수신

---

## M7. 통합 테스트 + 문서화

- [ ] Testcontainers 통합 테스트
- [ ] E2E 시나리오 검증 (경매 생성 → 입찰 → 마감 → 낙찰 알림)
- [ ] docs/ 문서 최종 정리
- [ ] README 최종 정리
- [ ] AI 워크플로우 도입 (선택)
  - [ ] `ai-doc-update.yml` — main 머지 시 docs/ 자동 업데이트 PR 생성
  - [ ] `ai-issue-analysis.yml` — `analyze` 라벨 추가 시 이슈 원인/해결 방향 코멘트

**완료 기준**: 전체 시나리오 정상 동작

---

## M8. GKE 배포

- [ ] Terraform GKE 인프라 (cloud-sre-platform 연계)
- [ ] Kubernetes 매니페스트 작성
- [ ] ArgoCD GitOps 연동
- [ ] Strimzi Kafka Operator

**완료 기준**: GKE 위에서 전체 서비스 동작

---

## 이슈

진행 중 발생한 이슈는 [docs/ISSUES.md](docs/ISSUES.md)에서 관리합니다.
