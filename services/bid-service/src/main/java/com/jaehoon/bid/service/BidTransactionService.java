package com.jaehoon.bid.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jaehoon.bid.dto.BidResponse;
import com.jaehoon.bid.entity.Bid;
import com.jaehoon.bid.entity.BidStatus;
import com.jaehoon.bid.entity.OutboxEvent;
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
                .eventType(OUTBOX_EVENT_TYPE_BID_PLACED)
                .payload(buildBidPlacedPayload(bid))
                .build());

        return BidResponse.from(bid);
    }

    private Map<String, Object> buildBidPlacedPayload(Bid bid) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", UUID.randomUUID().toString());
        payload.put("eventType", OUTBOX_EVENT_TYPE_BID_PLACED);
        payload.put("bidId", bid.getId().toString());
        payload.put("auctionId", bid.getAuctionId().toString());
        payload.put("bidderId", bid.getBidderId().toString());
        payload.put("amount", bid.getAmount());
        payload.put("occurredAt", bid.getPlacedAt().toEpochMilli());
        return payload;
    }
}
