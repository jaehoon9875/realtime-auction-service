# ISSUES.md

프로젝트 진행 중 발생한 이슈, 미해결 항목, 보류 결정 사항을 기록합니다.

---

## 심각도 기준

| 심각도 | 기준 |
|--------|------|
| 🔴 높음 | 다음 단계 진행을 막는 블로커 |
| 🟡 중간 | 기능에 영향은 있으나 우회 방법이 존재 |
| 🟢 낮음 | 사소한 불편, 나중에 처리해도 무방 |

---

## 진행 중 이슈

| # | 심각도 | 마일스톤 | 제목 | 발생일 | 상태 |
|---|--------|---------|------|--------|------|
| - | - | - | (아직 없음) | - | - |

---

## 보류 / 결정 필요

| # | 심각도 | 항목 | 내용 | 등록일 |
|---|--------|------|------|--------|
| 3 | 🟡 중간 | State Store 초기 `currentPrice` | 입찰 0건일 때 `GET /api/auctions/{id}`의 `currentPrice`를 State Store에서 어떻게 **시작가와 일치**시킬지(예: `auction-events` 시드, 기본값 규칙) 문서에 없음. | 2026-05-05 |
| 4 | 🟢 낮음 | `BID_REJECTED` 클라이언트 알림 | `docs/kafka.md`에 `BID_REJECTED` 이벤트는 있으나 `docs/api.md` WebSocket 메시지에 **입찰 거부** 타입 없음. 실시간 거부 UX 미정. | 2026-05-05 |

---

## 해결된 이슈

| # | 심각도 | 마일스톤 | 제목 | 해결일 | 해결 방법 |
|---|--------|---------|------|--------|-----------|
| 2 | 🟡 중간 | docs | 마감 후 `auctions.status` 동기화 (정책 확정) | 2026-05-05 | **역할 분리 확정**: Auction Service DB의 **`CLOSED`는 Auction Service가 책임**(`endsAt` 경과 시 스케줄러로 반영·명시적 전이). Kafka Streams **`AUCTION_CLOSED`/notification** 은 **알림·실시간 파이프라인용**, DB 진실 원본 아님. 입찰은 **`endsAt` + 상태** 검증(문서화). 정본: `docs/architecture.md`「경매 생명주기와 마감 정책」, `docs/kafka.md` 주석. **코드(마감 스케줄러 구현)** 는 별도 작업. |
| 1 | 🟡 중간 | auction-service | 경매 상태 정합 + `startsAt`·PENDING→ONGOING (구 보류 이슈 1) | 2026-05-05 | **용어·종료**: 코드·DB 진행 중 상태 **`ONGOING`** 통일, 종료는 **`CLOSED`** 만 (`CANCELLED`/`AUCTION_CANCELLED` 문서·설명 정리). Flyway **`V4`** CHECK·`ACTIVE`→`ONGOING` 마이그레이션. **시작·전환**: **`starts_at`** 컬럼·API **`startsAt`** (생략 시 생성 시각), 생성 시 `startsAt > now` → **`PENDING`**, 그 외 **`ONGOING`**. **`AuctionStartScheduler`**·`activateDueAuctions()` 로 예약 경매 시작 시 **`AUCTION_STATUS_CHANGED`** Outbox. Outbox/Avro에 **`startsAt`** epoch, `docs/schema.md`·`api.md`·`kafka.md`·`CLAUDE.md` 동기화. 입찰 시점 검증(Bid Service)은 후속. |

---

## 이슈 작성 방법

이슈 발생 시 "진행 중 이슈" 테이블에 추가합니다.

```
| 1 | 🔴 높음 | M1 | docker-compose Debezium 연결 실패 | 2024-xx-xx | 조사 중 |
```

해결되면 해결된 이슈 섹션으로 옮기고 해결 방법을 기록합니다.
