package com.jaehoon.streams.auction.store;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuctionBidStateTest {

    @Test
    void initial_시작가로_초기화되고_낙찰자는_null이다() {
        AuctionBidState state = AuctionBidState.initial(5000L);

        assertThat(state.highestBid()).isEqualTo(5000L);
        assertThat(state.highestBidderId()).isNull();
        assertThat(state.bidCount()).isEqualTo(0);
    }

    @Test
    void initial_시작가_0도_허용된다() {
        AuctionBidState state = AuctionBidState.initial(0L);

        assertThat(state.highestBid()).isEqualTo(0L);
    }
}
