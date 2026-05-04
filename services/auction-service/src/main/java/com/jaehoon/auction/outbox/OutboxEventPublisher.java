package com.jaehoon.auction.outbox;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.jaehoon.auction.entity.Auction;
import com.jaehoon.auction.entity.OutboxEvent;
import com.jaehoon.auction.repository.OutboxEventRepository;

import lombok.RequiredArgsConstructor;

/**
 * Outbox 테이블에 이벤트를 기록하는 헬퍼.
 * 반드시 AuctionService 의 @Transactional 범위 안에서 호출해야 한다.
 * Debezium 이 WAL을 읽어 auction-events 토픽으로 전달한다 (직접 Kafka 발행 금지).
 */
@Component
@RequiredArgsConstructor
public class OutboxEventPublisher {

    private final OutboxEventRepository outboxEventRepository;

    /**
     * 경매 도메인 이벤트를 outbox_events 테이블에 저장한다.
     *
     * @param auction   이벤트 원본 경매 엔티티
     * @param eventType {@code AUCTION_CREATED} 또는 {@code AUCTION_STATUS_CHANGED}
     */
    public void publish(Auction auction, String eventType) {
        Map<String, Object> payload = buildPayload(auction, eventType);

        OutboxEvent event = OutboxEvent.builder()
                .aggregateType("AUCTION")
                .aggregateId(auction.getId())
                .eventType(eventType)
                .payload(payload)
                .build();

        // 호출부의 트랜잭션과 동일 커밋에 포함됨 — 원자성 보장
        outboxEventRepository.save(event);
    }

    /** payload 키 순서·이름은 {@code infra/avro/AuctionEvent.avsc} 와 동기화한다. */
    private Map<String, Object> buildPayload(Auction auction, String eventType) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", UUID.randomUUID().toString());
        payload.put("eventType", eventType);
        payload.put("auctionId", auction.getId().toString());
        payload.put("sellerId", auction.getSellerId().toString());
        payload.put("status", auction.getStatus().name());
        payload.put("title", auction.getTitle());
        payload.put("startPrice", auction.getStartPrice());
        // auction-service 는 입찰 정보를 모름. currentPrice 의 진짜 주인은 Kafka Streams State Store.
        // AUCTION_CLOSED 는 Punctuator 가 발행하며 그때 실제 최고가가 채워진다.
        payload.put("currentPrice", null);
        // endsAt을 Unix epoch(초) 로 변환 — Debezium/Kafka 소비자 호환
        payload.put("endsAt", auction.getEndsAt().toEpochSecond(java.time.ZoneOffset.UTC));
        payload.put("occurredAt", Instant.now().getEpochSecond());
        return payload;
    }
}
