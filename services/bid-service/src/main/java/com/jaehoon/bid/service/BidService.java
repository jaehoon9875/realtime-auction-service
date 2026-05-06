package com.jaehoon.bid.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jaehoon.bid.dto.BidResponse;
import com.jaehoon.bid.exception.AuctionNotFoundException;
import com.jaehoon.bid.exception.BadRequestException;
import com.jaehoon.bid.repository.BidRepository;
import com.jaehoon.bid.service.AuctionServiceClient.AuctionSnapshot;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BidService {

    private static final String AUCTION_STATUS_ONGOING = "ONGOING";

    private final BidRepository bidRepository;
    private final AuctionServiceClient auctionServiceClient;
    private final AuctionStreamsClient auctionStreamsClient;
    private final BidTransactionService bidTransactionService;

    // 검증은 트랜잭션 밖에서 수행하고, DB 저장만 BidTransactionService에 위임한다.
    public BidResponse placeBid(UUID bidderId, UUID auctionId, Long amount) {
        AuctionSnapshot auction = auctionServiceClient.getAuction(auctionId);
        if (auction == null) {
            throw new AuctionNotFoundException(auctionId);
        }

        validateAuctionOpen(auction);
        validateBidAmount(auction, amount, auctionStreamsClient.getCurrentPrice(auctionId));

        return bidTransactionService.saveBidWithOutbox(bidderId, auctionId, amount);
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
}
