package com.jaehoon.auction.dto;

import java.time.Instant;
import java.util.UUID;

import com.jaehoon.auction.entity.Auction;
import com.jaehoon.auction.entity.AuctionStatus;

/**
 * 경매 응답 DTO.
 * currentPrice 는 Kafka Streams State Store 에서 조회하며, M5 이전 또는 조회 실패 시 null 이다.
 */
public record AuctionResponse(
        UUID id,
        UUID sellerId,
        String title,
        String description,
        Long startPrice,
        Long currentPrice,
        AuctionStatus status,
        Instant startsAt,
        Instant endsAt,
        Instant createdAt,
        Instant updatedAt
) {
    /** Auction 엔티티와 currentPrice 로부터 응답 DTO 를 생성한다. */
    public static AuctionResponse from(Auction auction, Long currentPrice) {
        return new AuctionResponse(
                auction.getId(),
                auction.getSellerId(),
                auction.getTitle(),
                auction.getDescription(),
                auction.getStartPrice(),
                currentPrice,
                auction.getStatus(),
                auction.getStartsAt(),
                auction.getEndsAt(),
                auction.getCreatedAt(),
                auction.getUpdatedAt()
        );
    }
}
