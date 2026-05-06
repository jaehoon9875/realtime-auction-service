package com.jaehoon.bid.service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jaehoon.bid.dto.BidResponse;
import com.jaehoon.bid.entity.Bid;
import com.jaehoon.bid.entity.BidStatus;
import com.jaehoon.bid.entity.OutboxEvent;
import com.jaehoon.bid.exception.AuctionNotFoundException;
import com.jaehoon.bid.exception.BadRequestException;
import com.jaehoon.bid.repository.BidRepository;
import com.jaehoon.bid.repository.OutboxEventRepository;
import com.jaehoon.bid.service.AuctionServiceClient.AuctionSnapshot;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BidService {

    private static final String OUTBOX_AGGREGATE_TYPE = "BID";
    private static final String OUTBOX_EVENT_TYPE_BID_PLACED = "BID_PLACED";
    private static final String AUCTION_STATUS_ONGOING = "ONGOING";

    private final BidRepository bidRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final AuctionServiceClient auctionServiceClient;
    private final AuctionStreamsClient auctionStreamsClient;

    /**
     * 입찰 검증 후 저장 + Outbox 이벤트 기록을 하나의 트랜잭션으로 처리한다.
     */
    @Transactional
    public BidResponse placeBid(UUID bidderId, UUID auctionId, Long amount) {
        AuctionSnapshot auction = auctionServiceClient.getAuction(auctionId);
        if (auction == null) {
            throw new AuctionNotFoundException(auctionId);
        }

        validateAuctionOpen(auction);
        validateBidAmount(auction, amount, auctionStreamsClient.getCurrentPrice(auctionId));

        // 도메인 데이터와 Outbox 이벤트를 같은 커밋에 묶어 유실 없이 Debezium으로 전달한다.
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

    public Page<BidResponse> getMyBids(UUID bidderId, Pageable pageable) {
        return bidRepository.findByBidderIdOrderByPlacedAtDesc(bidderId, pageable)
                .map(BidResponse::from);
    }

    private void validateAuctionOpen(AuctionSnapshot auction) {
        if (!AUCTION_STATUS_ONGOING.equals(auction.status())) {
            throw new BadRequestException("진행 중인 경매가 아닙니다.");
        }
        // 마감 시각 비교는 절대 시각(Instant) 기준으로 판단한다.
        if (!auction.endsAt().isAfter(Instant.now())) {
            throw new BadRequestException("마감된 경매입니다.");
        }
    }

    private void validateBidAmount(AuctionSnapshot auction, Long amount, Long currentPrice) {
        if (currentPrice == null) {
            if (amount <= auction.startPrice()) {
                throw new BadRequestException("시작가보다 높아야 합니다.");
            }
            return;
        }
        if (amount <= currentPrice) {
            throw new BadRequestException("현재 최고가보다 높아야 합니다.");
        }
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
