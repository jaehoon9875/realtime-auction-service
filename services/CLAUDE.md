# services/CLAUDE.md

4개 서비스(auction, bid, user, notification)에 공통으로 적용되는 Java/Spring Boot 개발 원칙.
서비스별 도메인 상세는 각 서비스 하위 `CLAUDE.md` 참고.

---

## 공통 기술 스택

- **Java 17**, **Spring Boot 3.x**
- **Gradle Kotlin DSL** (멀티모듈 빌드)
- **PostgreSQL** (서비스별 독립 DB, 다른 서비스 DB 직접 접근 금지)
- **Spring Data JPA** + **Flyway** (마이그레이션)
- **Testcontainers** (통합 테스트)

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

## 외부 서비스 호출

- 서비스 간 REST 호출에는 **반드시 Resilience4j Circuit Breaker를 적용**한다.
- `WebClient`를 사용하며 동기 블로킹(`block()`) 호출은 금지한다.
- Retry는 Circuit Breaker와 함께 구성한다 (최대 3회, exponential backoff).

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
