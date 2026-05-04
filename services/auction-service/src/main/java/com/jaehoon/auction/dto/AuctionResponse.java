package com.jaehoon.auction.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import com.jaehoon.auction.entity.Auction;

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
        String status,
        LocalDateTime endsAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
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
                auction.getEndsAt(),
                auction.getCreatedAt(),
                auction.getUpdatedAt()
        );
    }
}
