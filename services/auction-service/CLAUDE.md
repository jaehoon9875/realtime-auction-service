# services/auction-service/CLAUDE.md

경매 CRUD API와 Outbox Pattern을 통한 Kafka 이벤트 발행을 담당하는 서비스.

---

## 도메인 책임

- 경매 생성, 조회, 수정, 삭제 (CRUD)
- 경매 상태 관리: `PENDING` → `ACTIVE` → `CLOSED`
- Outbox 테이블에 `auction-events` 이벤트 기록 (Debezium이 읽어 Kafka 발행)
- `currentPrice` 조회는 DB가 아닌 **Kafka Streams State Store REST API**에서 조회

---

## 주요 엔드포인트

| Method | Path | 설명 |
|--------|------|------|
| POST | `/auctions` | 경매 생성 |
| GET | `/auctions/{id}` | 경매 상세 조회 (currentPrice는 State Store 조회) |
| GET | `/auctions` | 경매 목록 조회 |
| PATCH | `/auctions/{id}/status` | 경매 상태 변경 (내부 API) |

---

## Outbox 이벤트 타입

| eventType | 발행 시점 |
|-----------|-----------|
| `AUCTION_CREATED` | 경매 생성 |
| `AUCTION_STATUS_CHANGED` | 상태 변경 (ACTIVE, CLOSED) |

---

## 핵심 설계 주의사항

- 경매 생성/상태 변경 시 **도메인 저장 + Outbox 저장을 하나의 `@Transactional`로 묶는다.**
- `currentPrice`는 DB의 `auctions` 테이블에 저장하지 않는다. 조회 시 `auction-streams` Interactive Queries API를 호출한다.
- `auction-streams` 호출에 Circuit Breaker를 적용한다. 장애 시 fallback은 `currentPrice: null` 반환.

---

## 의존 서비스

| 서비스 | 방식 | 용도 |
|--------|------|------|
| auction-streams | WebClient (Circuit Breaker) | currentPrice 조회 |
| Debezium | CDC (자동) | Outbox → auction-events 발행 |

---

## DB 스키마 참고

docs/schema.md의 auction-service 섹션 참고.
