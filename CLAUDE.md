# CLAUDE.md

이 파일은 AI 어시스턴트가 프로젝트 전체 문맥을 파악하기 위한 파일입니다.
각 하위 디렉토리의 상세 문맥은 해당 디렉토리의 CLAUDE.md를 참고하세요.

## 프로젝트 개요

Kafka Streams 기반 실시간 경매 서비스. 입찰 이벤트를 Kafka Streams로 실시간 처리하고,
Debezium CDC를 활용한 Outbox Pattern, WebSocket 기반 실시간 알림을 구현한 MSA 프로젝트.
주요 디렉토리: services/(api-gateway + auction·bid·user·notification), streams/(Kafka Streams App), infra/(인프라), docs/(설계 문서)

## 현재 진행 상태

M1·M2·M3·M4 완료. M5(Kafka Streams App) 진행 예정.

단계별 체크리스트: docs/PLAN.md
진행 중 이슈: docs/ISSUES.md

## 아키텍처 원칙

1. 이벤트 유실 금지: DB 저장과 Kafka 발행은 반드시 Outbox Pattern + Debezium으로 처리한다. 직접 Kafka 발행하지 않는다.
2. 상태는 State Store: 경매 최고가는 DB가 아닌 Kafka Streams State Store에서 관리한다.
3. 경매 종료 표시(`auctions.status=CLOSED`)는 Auction Service DB가 책임. 시간 마감은 `endsAt` 기준 스케줄러로 반영하고, Streams 마감 이벤트는 알림·실시간 파이프라인용. 상세는 docs/architecture.md 「경매 생명주기와 마감 정책」.
4. 서비스 독립: 각 서비스는 독립된 DB를 가진다. 서비스 간 직접 DB 접근 및 JOIN 금지. 데이터 공유는 Kafka 이벤트로만 한다.
5. 장애 격리: 서비스 간 REST 호출에는 반드시 Circuit Breaker를 적용한다.

## 개발 규칙

- 커밋: `type: 설명` (type 목록: feat, fix, refactor, docs, infra, test, chore)
- 브랜치: feature/{기능명}, fix/{버그명}
- 코드 작성 시 주요 로직에는 한글 주석을 작성한다.

## 보안 규칙

- 환경변수는 하드코딩하지 않는다. K8s ConfigMap/Secret 또는 .env 파일로 관리한다.
- API 키, DB 비밀번호, JWT secret 등 민감 정보는 코드에 하드코딩하지 않는다. .env 파일은 반드시 .gitignore에 포함.
- GitHub에 push 전, 민감 정보가 포함된 파일이 스테이징되어 있지 않은지 반드시 확인한다.

## AI 행동 원칙

- 모호한 요구사항은 추측하지 않는다. 모호한 점이 있다면 코딩 전 질문하고, 구현 방식이 여럿이면 트레이드오프를 먼저 제시한다.
- 문제를 해결하는 최소한의 코드만 작성한다. 요청하지 않은 추상화·유연성·라이브러리 도입을 하지 않으며, 항상 단순함을 유지한다.
- 요청받은 부분만 수정한다. 관련 없는 코드·주석·서식을 건드리지 않는다. 불필요한 코드 발견 시 삭제 말고 보고한다.
- 모든 작업은 검증 가능한 목표를 중심으로 수행한다. 복잡한 작업은 실행 전 단계별 계획을 먼저 공유하고, 버그 수정은 재현 테스트 작성 → 통과 순서로 진행한다.

## 문서 작성 원칙

- README.md (사람용): 가독성 우선, 테이블/링크/코드블록 활용, 해당 경로 개요 + docs/ 네비게이션 역할
- CLAUDE.md (AI용): 정보 밀도 우선, 마크다운 장식(테이블, 하이퍼링크) 최소화, AI 문맥 파악 핵심 정보만
- docs/ 원칙: 핵심 문서는 docs/에서 중앙 관리, 각 디렉토리 README.md는 관련 docs/ 문서로 링크
