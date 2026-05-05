# services/bid-service/CLAUDE.md

입찰 처리와 유효성 검증을 담당하는 서비스.
입찰 전 경매 상태와 최고가를 State Store에서 검증하고, Outbox로 `bid-events`를 발행한다.

---

## 도메인 책임

- 입찰 요청 수신 및 유효성 검증
  - 경매가 `ONGOING` 상태인지 확인
  - 입찰가가 현재 최고가보다 높은지 확인 (State Store 조회)
  - 경매 마감 시각(`endsAt`) 검증 — 역할 분담: `services/CLAUDE.md` 「경매 마감·상태 책임」
- 입찰 저장 + Outbox에 `bid-events` 이벤트 기록

---

## 주요 엔드포인트

| Method | Path | 설명 |
|--------|------|------|
| POST | `/bids` | 입찰 요청 |
| GET | `/bids/{auctionId}` | 특정 경매의 입찰 내역 조회 |

---

## 유효성 검증 흐름

```
입찰 요청
  └─ auction-streams Interactive Queries 조회 (경매 상태 + 현재 최고가)
       ├─ 경매 ONGOING 아님 → 400 반환
       ├─ 입찰가 ≤ 현재 최고가 → 400 반환
       └─ 유효 → 입찰 저장 + Outbox 저장 (단일 트랜잭션)
```

---

## Outbox 이벤트 타입

| eventType | 발행 시점 |
|-----------|-----------|
| `BID_PLACED` | 유효한 입찰 완료 |

---

## Resilience4j 적용

`auction-streams` 호출은 Circuit Breaker + Retry를 반드시 적용한다.

- Circuit Breaker: `auction-streams` 장애 시 빠른 실패(fail-fast) → 503 반환
- Retry: 일시적 네트워크 오류 시 최대 3회, exponential backoff
- **Fallback 없음**: 현재 최고가를 알 수 없는 상태에서 입찰을 수락하면 데이터 정합성이 깨진다.

---

## 의존 서비스

| 서비스 | 방식 | 용도 |
|--------|------|------|
| auction-streams | WebClient (Circuit Breaker + Retry) | 경매 상태 + 현재 최고가 검증 |
| Debezium | CDC (자동) | Outbox → bid-events 발행 |

---

## DB 스키마 참고

docs/schema.md의 bid-service 섹션 참고.
