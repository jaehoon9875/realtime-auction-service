package com.jaehoon.bid.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jaehoon.auction.avro.BidEvent;
import com.jaehoon.bid.dto.BidResponse;
import com.jaehoon.bid.entity.Bid;
import com.jaehoon.bid.entity.BidStatus;
import com.jaehoon.bid.entity.OutboxEvent;
import com.jaehoon.bid.outbox.OutboxAvroSerializer;
import com.jaehoon.bid.repository.BidRepository;
import com.jaehoon.bid.repository.OutboxEventRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BidTransactionService {

    private static final String OUTBOX_AGGREGATE_TYPE = "BID";
    private static final String OUTBOX_EVENT_TYPE_BID_PLACED = "BID_PLACED";

    private final BidRepository bidRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final OutboxAvroSerializer outboxAvroSerializer;

    // 도메인 데이터와 Outbox 이벤트를 같은 커밋에 묶어 유실 없이 Debezium으로 전달한다.
    @Transactional
    public BidResponse saveBidWithOutbox(UUID bidderId, UUID auctionId, Long amount) {
        Bid bid = bidRepository.save(Bid.builder()
                .auctionId(auctionId)
                .bidderId(bidderId)
                .amount(amount)
                .status(BidStatus.ACCEPTED)
                .build());

        outboxEventRepository.save(OutboxEvent.builder()
                .aggregateType(OUTBOX_AGGREGATE_TYPE)
                .aggregateId(bid.getId())
                .eventKey(auctionId)
                .eventType(OUTBOX_EVENT_TYPE_BID_PLACED)
                .payload(buildBidPlacedPayload(bid))
                .build());

        return BidResponse.from(bid);
    }

    private byte[] buildBidPlacedPayload(Bid bid) {
        BidEvent event = BidEvent.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setEventType(OUTBOX_EVENT_TYPE_BID_PLACED)
                .setBidId(bid.getId().toString())
                .setAuctionId(bid.getAuctionId().toString())
                .setBidderId(bid.getBidderId().toString())
                .setAmount(bid.getAmount())
                .setOccurredAt(bid.getPlacedAt().toEpochMilli())
                .build();
        return outboxAvroSerializer.serialize(event);
    }
}
