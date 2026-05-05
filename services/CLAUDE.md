# services/CLAUDE.md

4개 서비스(auction, bid, user, notification)에 공통으로 적용되는 Java/Spring Boot 개발 원칙.
**경매 마감·`CLOSED`·입찰 시각 검증의 역할 분담**은 아래 「경매 마감·상태 책임」에만 둔다(서비스별 `CLAUDE.md`에 장문 복붙하지 않음).
서비스별 도메인 상세는 각 서비스 하위 `CLAUDE.md` 참고.

---

## 공통 기술 스택

- **Java 21**, **Spring Boot 4.x**
- **Gradle Kotlin DSL** (멀티모듈 빌드)
- **PostgreSQL** (서비스별 독립 DB, 다른 서비스 DB 직접 접근 금지)
- **Spring Data JPA** + **Flyway** (마이그레이션)
- **Testcontainers** (통합 테스트)
- **Java 21 Virtual Threads** (`spring.threads.virtual.enabled=true`, Servlet 서비스에 적용)

---

## 서비스별 런타임 모델

| 서비스 | 모델 | 이유 |
|--------|------|------|
| api-gateway | WebFlux | Spring Cloud Gateway 필수 요건 |
| auction-service | Servlet + Virtual Threads | CRUD + JPA 위주, 단순한 동기 모델 |
| bid-service | Servlet + Virtual Threads | CRUD + JPA 위주, 단순한 동기 모델 |
| user-service | Servlet + Virtual Threads | CRUD + JPA 위주, 단순한 동기 모델 |
| notification-service | WebFlux | 수천 개의 장기 WebSocket 연결 유지, 이벤트 루프 모델이 유리 |

WebFlux는 notification-service에만 사용한다. 나머지 서비스는 JPA와의 자연스러운 통합과 코드 단순성을 위해 Servlet + Virtual Threads를 택한다. Virtual Threads(Java 21)가 Servlet의 동시성 한계를 해소하므로 WebFlux의 주요 도입 근거가 사라진다.

---

## 레이어 구조

```
controller → service → repository → entity
```

- `controller`: HTTP 요청/응답, 입력 유효성 검증(Bean Validation). 비즈니스 로직 없음.
- `service`: 비즈니스 로직, 트랜잭션 경계, 외부 서비스 호출.
- `repository`: Spring Data JPA 인터페이스 또는 JPQL 쿼리. DB 접근만.
- `entity`: JPA 엔티티. 비즈니스 로직 없음. `@Getter`만 허용, setter 금지.

controller에서 repository를 직접 호출하지 않는다.

---

## 패키지 구조 (서비스 공통)

```
{service}/
├── src/main/java/com/auction/{service}/
│   ├── controller/
│   ├── service/
│   ├── repository/
│   ├── entity/
│   ├── dto/              # 요청/응답 DTO (record 사용)
│   ├── exception/        # 커스텀 예외 + GlobalExceptionHandler
│   ├── config/           # Spring 설정 클래스
│   └── outbox/           # Outbox 관련 (auction/bid 서비스만)
├── src/main/resources/
│   ├── application.yml
│   └── db/migration/     # Flyway 마이그레이션
└── src/test/
    ├── unit/             # 서비스 레이어 단위 테스트 (Mockito)
    └── integration/      # Testcontainers 통합 테스트
```

---

## Outbox Pattern 규칙

auction-service와 bid-service에만 해당.

- Kafka에 **직접 발행하지 않는다.** 반드시 Outbox 테이블 → Debezium 경로를 따른다.
- 도메인 이벤트 저장과 Outbox 저장은 **같은 트랜잭션** 안에서 처리한다.
- Outbox 레코드는 `aggregateType`, `aggregateId`, `eventType`, `payload`(Avro JSON) 필드를 포함한다.

---

## 경매 마감·상태 책임 (MSA · 이 파일이 정본)

인간/AI가 서비스 경계를 헷갈리지 않도록 **한곳**에만 둔다. 도메인 전체·시간·스케줄 가이드는 `docs/architecture.md` 「경매 생명주기와 마감 정책」.

| 구분 | 책임 |
|------|------|
| **Auction Service DB `auctions.status`** | **`CLOSED`를 비즈니스 상 “종료”로 쓸 때의 진실 원본.** 시간 마감은 `endsAt`(UTC) 지난 `ONGOING`을 **본 서비스 스케줄러**로 `CLOSED` 반영(+ Outbox). 조기 종료는 API로 명시 전이. Kafka Streams 토픽으로 DB를 맞추지 않는다. |
| **Bid Service 입찰 검증** | **`endsAt`과 요청 시각 비교**로 마감 후 입찰 차단. 스케줄러가 아직 `CLOSED`로 안 바꿨어도 **`endsAt` 이후면 거절**해야 한다. |
| **Kafka Streams (`auction-streams`)** | Punctuator 등으로 **`AUCTION_CLOSED`·낙찰 관련 이벤트** → **`notification-events` 등 실시간·알림 파이프라인**. Auction DB의 `CLOSED`를 대체하지 않는다. |
| **Notification Service** | `notification-events` 소비·WebSocket 푸시. **`AUCTION_CLOSED` 수신은 클라이언트 알림용**이며 Auction DB 상태와 별개. |

---

## 외부 서비스 호출

- 서비스 간 REST 호출에는 **반드시 Resilience4j Circuit Breaker를 적용**한다.
- Retry는 Circuit Breaker와 함께 구성한다 (최대 3회, exponential backoff).
- HTTP 클라이언트 선택:
  - **Servlet 서비스** (auction, bid, user): `RestClient` 사용 (Spring 6.1+, 동기). `WebClient + block()` 조합은 쓰지 않는다.
  - **WebFlux 서비스** (notification): `WebClient` 사용 (비동기), `block()` 호출 금지.

---

## 환경변수 및 설정

- 환경변수는 `application.yml`에서 `${ENV_VAR}` 형태로만 참조한다.
- 민감 정보(DB 비밀번호, JWT secret 등)는 코드에 하드코딩하지 않는다.
- 로컬 개발용 값은 `.env` 파일로 관리하며 `.gitignore`에 반드시 포함한다.

---

## 코드 작성 규칙

- 주요 비즈니스 로직(트랜잭션 경계, 상태 전이, 이벤트 발행 지점)에는 **한글 주석**을 작성한다.
- DTO는 Java `record`를 사용한다.
- 예외는 `GlobalExceptionHandler`(@RestControllerAdvice)에서 일괄 처리한다.
  - `400`: 클라이언트 오류 (재시도 불필요)
  - `404`: 리소스 없음
  - `503`: 의존 서비스 장애 (재시도 가능)

---

## 테스트

- 기능 구현 시 테스트 코드를 함께 작성한다.
- 단위 테스트: Mockito로 외부 의존성 mock, 서비스 레이어 로직 검증.
- 통합 테스트: Testcontainers로 PostgreSQL 실제 사용, 트랜잭션 롤백으로 격리.
- Outbox 패턴 검증은 통합 테스트에서 Outbox 테이블 직접 조회로 확인한다.
