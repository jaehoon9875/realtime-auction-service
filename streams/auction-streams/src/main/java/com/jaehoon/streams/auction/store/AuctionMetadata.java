package com.jaehoon.streams.auction.store;

/**
 * auction-metadata State Store에 저장되는 마감 판정용 경매 메타데이터.
 */
public record AuctionMetadata(
        long endsAt,
        long startPrice,
        String title
) {
}
