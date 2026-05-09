package com.jaehoon.streams.auction.web.dto;

public record AuctionStatusResponse(
        String auctionId,
        boolean active,
        long endsAt
) {
}
