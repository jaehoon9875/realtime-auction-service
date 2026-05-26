package com.jaehoon.streams.auction.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.jaehoon.streams.auction.store.AuctionBidState;

import tools.jackson.databind.json.JsonMapper;

/**
 * StreamsSerdeConfig State Store JSON Serde와 동일한 JsonMapper 직렬화 패턴 검증.
 * 운영에서는 Spring Boot auto-config JsonMapper 빈을 주입한다.
 */
class AuctionBidStateJsonSerdeTest {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Test
    void jsonMapper_로_State_Store_값을_직렬화_역직렬화한다() throws Exception {
        AuctionBidState original = new AuctionBidState(1_500_000L, "bidder-1", 3);

        byte[] bytes = jsonMapper.writeValueAsBytes(original);
        AuctionBidState restored = jsonMapper.readValue(bytes, AuctionBidState.class);

        assertThat(restored).isEqualTo(original);
    }
}
