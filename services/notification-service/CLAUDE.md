# services/notification-service/CLAUDE.md

Kafka `notification-events` 토픽을 소비하여 WebSocket으로 클라이언트에 실시간 알림을 전달하는 서비스.

---

## 도메인 책임

- `notification-events` Kafka 토픽 소비
- 수신 이벤트를 대상 사용자(또는 경매 구독자)의 WebSocket 세션으로 push
- Redis를 통한 WebSocket 세션 관리 (멀티 인스턴스 지원)
- Kafka 알림과 Auction DB `status` 관계: `services/CLAUDE.md` 「경매 마감·상태 책임」

---

## WebSocket 엔드포인트

| Path | 설명 |
|------|------|
| `/ws/auctions/{auctionId}` | 특정 경매 실시간 최고가/상태 수신 |
| `/ws/users/me` | 낙찰 알림 등 개인 알림 수신 |

---

## Redis 세션 관리

스케일아웃 시 특정 사용자/경매 구독자의 WebSocket 세션이 어느 인스턴스에 있는지 알 수 없다.

- **연결 시**: `auction:{auctionId}:sessions` → instanceId 매핑을 Redis에 저장
- **이벤트 수신 시**: 대상 세션이 현재 인스턴스에 있으면 직접 push, 없으면 Redis Pub/Sub으로 해당 인스턴스에 전달
- **연결 해제 시**: Redis에서 세션 정보 삭제

---

## 소비하는 이벤트 (notification-events 토픽)

| eventType | 수신자 | 내용 |
|-----------|--------|------|
| `BID_UPDATED` | 경매 구독자 전체 | 새 최고가, 입찰자 |
| `AUCTION_CLOSED` | 경매 구독자 전체 | 마감 + 낙찰자 정보 |
| `AUCTION_WON` | 낙찰자 개인 | 낙찰 알림 |

---

## 핵심 설계 주의사항

- Kafka Consumer는 `@KafkaListener`를 사용한다.
- WebSocket은 Spring WebSocket (`@EnableWebSocket`) 또는 STOMP 위에 구현한다.
- 세션 관리 로직이 복잡하므로 `WebSocketSessionRegistry` 컴포넌트로 분리한다.
- DB는 없다. 이 서비스는 **상태를 Redis에만 보관**한다.

---

## 의존 서비스

| 서비스 | 방식 | 용도 |
|--------|------|------|
| Kafka | KafkaListener | notification-events 소비 |
| Redis | Spring Data Redis + Pub/Sub | WebSocket 세션 공유 |
