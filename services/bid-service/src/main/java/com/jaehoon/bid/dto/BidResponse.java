package com.jaehoon.bid.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import com.jaehoon.bid.entity.Bid;
import com.jaehoon.bid.entity.BidStatus;

public record BidResponse(
        UUID id,
        UUID auctionId,
        UUID bidderId,
        Long amount,
        BidStatus status,
        LocalDateTime placedAt) {

    public static BidResponse from(Bid bid) {
        return new BidResponse(
                bid.getId(),
                bid.getAuctionId(),
                bid.getBidderId(),
                bid.getAmount(),
                bid.getStatus(),
                bid.getPlacedAt());
    }
}
