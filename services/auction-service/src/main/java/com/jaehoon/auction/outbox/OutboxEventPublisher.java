package com.jaehoon.auction.outbox;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.jaehoon.auction.entity.Auction;
import com.jaehoon.auction.entity.OutboxEvent;
import com.jaehoon.auction.events.AuctionEvent;
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
    private final OutboxAvroSerializer outboxAvroSerializer;

    /**
     * 경매 도메인 이벤트를 outbox_events 테이블에 저장한다.
     *
     * @param auction   이벤트 원본 경매 엔티티
     * @param eventType {@code AUCTION_CREATED} 또는 {@code AUCTION_STATUS_CHANGED}
     */
    public void publish(Auction auction, String eventType) {
        OutboxEvent event = OutboxEvent.builder()
                .aggregateType("AUCTION")
                .aggregateId(auction.getId())
                .eventType(eventType)
                .payload(buildPayload(auction, eventType))
                .build();

        // 호출부의 트랜잭션과 동일 커밋에 포함됨 — 원자성 보장
        outboxEventRepository.save(event);
    }

    /** 필드는 {@code infra/avro/AuctionEvent.avsc} 와 동기화한다. */
    private byte[] buildPayload(Auction auction, String eventType) {
        AuctionEvent event = AuctionEvent.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setEventType(eventType)
                .setAuctionId(auction.getId().toString())
                .setSellerId(auction.getSellerId().toString())
                .setStatus(auction.getStatus().name())
                .setTitle(auction.getTitle())
                .setStartPrice(auction.getStartPrice())
                // auction-service 는 입찰 정보를 모름. currentPrice 의 진짜 주인은 Kafka Streams State Store.
                .setCurrentPrice(null)
                .setStartsAt(auction.getStartsAt().getEpochSecond())
                .setEndsAt(auction.getEndsAt().getEpochSecond())
                .setOccurredAt(Instant.now().getEpochSecond())
                .build();
        return outboxAvroSerializer.serialize(event);
    }
}
