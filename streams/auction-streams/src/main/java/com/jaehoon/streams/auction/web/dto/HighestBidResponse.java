package com.jaehoon.streams.auction.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record HighestBidResponse(
        String auctionId,
        long highestBid,
        String highestBidderId,
        int bidCount
) {
}
