# services

4개 마이크로서비스 디렉토리입니다.

---

## 서비스 목록

| 서비스 | 역할 | 포트 |
|--------|------|------|
| auction-service | 경매 CRUD, Outbox 이벤트 발행 | 8081 |
| bid-service | 입찰 처리, 유효성 검증 | 8082 |
| user-service | 회원가입/로그인, JWT | 8083 |
| notification-service | Kafka 소비 → WebSocket push | 8084 |

---

## 패키지 구조 (서비스 공통)

```text
{service}/
├── src/main/java/com/auction/{service}/
│   ├── controller/
│   ├── service/
│   ├── repository/
│   ├── entity/
│   ├── dto/              # 요청/응답 DTO (record 사용)
│   ├── exception/        # 커스텀 예외 + GlobalExceptionHandler
│   ├── config/
│   └── outbox/           # auction/bid 서비스만
├── src/main/resources/
│   ├── application.yml
│   └── db/migration/     # Flyway 마이그레이션
└── src/test/
    ├── unit/
    └── integration/
```

---

## 관련 문서

| 문서 | 내용 |
|------|------|
| [docs/api.md](../docs/api.md) | REST API + WebSocket 엔드포인트 명세 |
| [docs/schema.md](../docs/schema.md) | 서비스별 DB 스키마 |
