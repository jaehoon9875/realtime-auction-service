# CLAUDE.md

이 파일은 AI 어시스턴트가 프로젝트 전체 문맥을 파악하기 위한 파일입니다.
각 하위 디렉토리의 상세 문맥은 해당 디렉토리의 CLAUDE.md를 참고하세요.

---

## 프로젝트 개요

**realtime-auction-service**
Kafka Streams 기반 실시간 경매 서비스. 입찰 이벤트를 Kafka Streams로 실시간 처리하고,
Debezium CDC를 활용한 Outbox Pattern, WebSocket 기반 실시간 알림을 구현한 MSA 프로젝트.

---

## 디렉토리 구조

```
realtime-auction-service/
├── services/        # 4개 마이크로서비스 (auction, bid, user, notification)
├── streams/         # Kafka Streams App
├── infra/           # docker-compose, K8s 매니페스트, Terraform
├── docs/            # 설계 문서, API 명세, 스키마, 이슈 추적
├── CLAUDE.md
└── README.md
```

각 디렉토리의 상세 문맥은 해당 디렉토리의 CLAUDE.md 참고.

---

## 기술 스택 요약

| 영역 | 기술 |
|------|------|
| Language | Java 17 |
| Framework | Spring Boot 3, Spring Cloud Gateway |
| 실시간 처리 | Kafka Streams |
| 이벤트 발행 | Debezium CDC + Outbox Pattern |
| 스키마 관리 | Confluent Schema Registry + Avro |
| 인증 | Spring Security + JWT (Refresh Token Rotation) |
| 장애 방어 | Resilience4j (Circuit Breaker) |
| DB | PostgreSQL (서비스별 독립) |
| Cache / 세션 | Redis (WebSocket 세션 공유) |
| 알림 | WebSocket |
| 빌드 | Gradle (Kotlin DSL) |
| 테스트 | Testcontainers |
| Infra | GKE + Terraform, Strimzi, ArgoCD |

---

## 핵심 설계 원칙

1. **이벤트 유실 금지**: DB 저장과 Kafka 발행은 반드시 Outbox Pattern + Debezium으로 처리한다. 직접 Kafka 발행하지 않는다.
2. **상태는 State Store**: 경매 최고가는 DB가 아닌 Kafka Streams State Store에서 관리한다.
3. **서비스 독립**: 각 서비스는 독립된 DB를 가진다. 서비스 간 직접 DB 접근은 금지한다.
4. **장애 격리**: 서비스 간 REST 호출에는 반드시 Circuit Breaker를 적용한다.
5. **GitOps**: 인프라 변경은 Git을 통해서만 반영한다 (ArgoCD).

---

## 현재 진행 상태

현재 Stage 1 시작 전 단계입니다. (전체 7 Stage)

단계별 체크리스트 → [docs/PLAN.md](docs/PLAN.md)
진행 중 이슈 → [docs/ISSUES.md](docs/ISSUES.md)

---

## 참고 문서

설계 문서, API 명세, 스키마, 이슈 추적 등 상세 문서 목록은 [docs/CLAUDE.md](docs/CLAUDE.md) 참고.

---

## Git Conventions

- 커밋 메시지: `type: 설명` (예: `feat: 경매 생성 API 구현`, `fix: Outbox 트랜잭션 누락 수정`)
- type 목록: `feat`, `fix`, `refactor`, `docs`, `infra`, `test`, `chore`
- 브랜치: `feature/{기능명}`, `fix/{버그명}` (예: `feature/auction-service`, `fix/debezium-connector`)

---

## Important Rules

- 서비스 간 DB를 직접 참조하거나 JOIN하지 않는다. 데이터 공유는 Kafka 이벤트로만 한다.
- Kafka에 직접 발행하지 않는다. 반드시 Outbox Table 저장 → Debezium 경로를 따른다.
- ArgoCD로 관리되는 리소스는 `kubectl apply`나 `helm upgrade` CLI로 직접 수정하지 않는다. 변경은 반드시 `infra/` 파일 수정 → Git push → ArgoCD sync 경로로만 반영한다.
- 환경변수는 하드코딩하지 않는다. K8s ConfigMap/Secret 또는 `.env` 파일로 관리한다.
- API 키, DB 비밀번호, JWT secret 등 민감 정보는 코드에 하드코딩하지 않는다. `.env` 파일은 반드시 `.gitignore`에 포함되어 있는지 확인한다.
- 코드 작성 시 주요 로직에는 한글 주석을 작성한다.
- GitHub에 push 전, 민감 정보가 포함된 파일이 스테이징되어 있지 않은지 반드시 확인한다.
