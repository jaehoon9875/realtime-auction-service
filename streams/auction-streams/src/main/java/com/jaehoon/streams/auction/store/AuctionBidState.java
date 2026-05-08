package com.jaehoon.streams.auction.store;

/**
 * auction-highest-bid State Store에 저장되는 경매별 최고 입찰 상태.
 */
public record AuctionBidState(
        long highestBid,
        String highestBidderId,
        int bidCount
) {

    public static AuctionBidState initial(long startPrice) {
        return new AuctionBidState(startPrice, null, 0);
    }
}
