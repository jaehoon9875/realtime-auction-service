# CLAUDE.md

이 파일은 AI 어시스턴트가 프로젝트 전체 문맥을 파악하기 위한 파일입니다.
각 하위 디렉토리의 상세 문맥은 해당 디렉토리의 CLAUDE.md를 참고하세요.

## 프로젝트 개요

Kafka Streams 기반 실시간 경매 서비스. 입찰 이벤트를 Kafka Streams로 실시간 처리하고,
Debezium CDC를 활용한 Outbox Pattern, WebSocket 기반 실시간 알림을 구현한 MSA 프로젝트.
주요 디렉토리: services/(api-gateway + auction·bid·user·notification), streams/(Kafka Streams App), infra/(인프라), docs/(설계 문서)

## 핵심 설계 원칙

1. 이벤트 유실 금지: DB 저장과 Kafka 발행은 반드시 Outbox Pattern + Debezium으로 처리한다. 직접 Kafka 발행하지 않는다.
2. 상태는 State Store: 경매 최고가는 DB가 아닌 Kafka Streams State Store에서 관리한다.
3. 경매 종료 표시(`auctions.status=CLOSED`)는 Auction Service DB가 책임. 시간 마감은 `endsAt` 기준 스케줄러로 반영하고, Streams 마감 이벤트는 알림·실시간 파이프라인용. 상세는 docs/architecture.md 「경매 생명주기와 마감 정책」.
4. 서비스 독립: 각 서비스는 독립된 DB를 가진다. 서비스 간 직접 DB 접근은 금지한다.
5. 장애 격리: 서비스 간 REST 호출에는 반드시 Circuit Breaker를 적용한다.
6. GitOps: 인프라 변경은 Git을 통해서만 반영한다 (ArgoCD).

## 현재 진행 상태

M1·M2 완료. M3(Auction Service) 구현 완료, E2E 검증·테스트 진행 중.

- GitHub 설정 완료 (CodeRabbit, Branch Protection)
- Gradle 멀티모듈 세팅 완료 (Spring Boot 4.0.6 / Java 21, user-service, api-gateway, auction-service 등록)
- docker-compose — `infra/docker-compose.yml`
- API Gateway 라우팅 기본 설정 — Spring Cloud Gateway 5.x (`spring-cloud-starter-gateway-server-webflux`)
- auction-service CRUD·Outbox·Debezium 구현 완료
- Gateway 내부 시크릿 헤더·인증/인가 역할 분리 완료

단계별 체크리스트: docs/PLAN.md
진행 중 이슈: docs/ISSUES.md

## 참고 문서

상세 문서 목록: docs/CLAUDE.md

## 문서 작성 원칙

README.md (사람용): 가독성 우선, 테이블/링크/코드블록 활용, 해당 경로 개요 + docs/ 네비게이션 역할
CLAUDE.md (AI용): 정보 밀도 우선, 마크다운 장식(테이블, 하이퍼링크) 최소화, AI 문맥 파악 핵심 정보만
docs/ 원칙: 핵심 문서는 docs/에서 중앙 관리, 각 디렉토리 README.md는 관련 docs/ 문서로 링크

## Git Conventions

커밋: `type: 설명` (type 목록: feat, fix, refactor, docs, infra, test, chore)
브랜치: feature/{기능명}, fix/{버그명}

## Important Rules

- 서비스 간 DB를 직접 참조하거나 JOIN하지 않는다. 데이터 공유는 Kafka 이벤트로만 한다.
- Kafka에 직접 발행하지 않는다. 반드시 Outbox Table 저장 → Debezium 경로를 따른다.
- ArgoCD로 관리되는 리소스는 kubectl apply나 helm upgrade CLI로 직접 수정하지 않는다. infra/ 파일 수정 → Git push → ArgoCD sync 경로로만 반영한다.
- 환경변수는 하드코딩하지 않는다. K8s ConfigMap/Secret 또는 .env 파일로 관리한다.
- API 키, DB 비밀번호, JWT secret 등 민감 정보는 코드에 하드코딩하지 않는다. .env 파일은 반드시 .gitignore에 포함.
- 코드 작성 시 주요 로직에는 한글 주석을 작성한다.
- GitHub에 push 전, 민감 정보가 포함된 파일이 스테이징되어 있지 않은지 반드시 확인한다.