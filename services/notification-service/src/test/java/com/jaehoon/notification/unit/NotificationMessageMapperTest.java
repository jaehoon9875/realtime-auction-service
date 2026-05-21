package com.jaehoon.notification.unit;

import static com.jaehoon.notification.kafka.NotificationTypes.AUCTION_CLOSED;
import static com.jaehoon.notification.kafka.NotificationTypes.BID_REJECTED;
import static com.jaehoon.notification.kafka.NotificationTypes.BID_UPDATED;
import static com.jaehoon.notification.kafka.NotificationTypes.OUTBID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaehoon.auction.events.NotificationEvent;
import com.jaehoon.notification.kafka.NotificationMappingException;
import com.jaehoon.notification.kafka.NotificationMessageMapper;
import com.jaehoon.notification.support.NotificationEventTestSupport;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Kafka NotificationEvent → WebSocket JSON 매핑 검증.
 */
class NotificationMessageMapperTest {

    private NotificationMessageMapper mapper;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        mapper = new NotificationMessageMapper(objectMapper);
    }

    @Test
    void BID_UPDATED_필드를_매핑한다() throws Exception {
        NotificationEvent event = baseEvent(BID_UPDATED, "auction-1")
                .setTargetAuctionId("auction-1")
                .setPayload(Map.of("currentPrice", "1500000", "bidCount", "3"))
                .build();

        JsonNode json = readJson(mapper.toWebSocketMessage(event));

        assertThat(json.get("type").asText()).isEqualTo(BID_UPDATED);
        assertThat(json.get("auctionId").asText()).isEqualTo("auction-1");
        assertThat(json.get("currentPrice").asLong()).isEqualTo(1_500_000L);
        assertThat(json.get("bidCount").asInt()).isEqualTo(3);
        assertThat(json.get("occurredAt").asText())
                .isEqualTo(NotificationEventTestSupport.OCCURRED_AT_ISO);
    }

    @Test
    void AUCTION_CLOSED_finalPrice_winnerId_fallback을_적용한다() throws Exception {
        NotificationEvent event = baseEvent(AUCTION_CLOSED, "auction-2")
                .setTargetAuctionId("auction-2")
                .setPayload(
                        Map.of(
                                "finalPrice", "2000000",
                                "winnerId", "user-winner",
                                "title", "테스트 경매"))
                .build();

        JsonNode json = readJson(mapper.toWebSocketMessage(event));

        assertThat(json.get("type").asText()).isEqualTo(AUCTION_CLOSED);
        assertThat(json.get("finalPrice").asLong()).isEqualTo(2_000_000L);
        assertThat(json.get("winnerId").asText()).isEqualTo("user-winner");
    }

    @Test
    void AUCTION_CLOSED_payload가_부족하면_null을_넣는다() throws Exception {
        NotificationEvent event = baseEvent(AUCTION_CLOSED, "auction-3")
                .setTargetAuctionId("auction-3")
                .setPayload(Map.of("title", "제목만"))
                .build();

        JsonNode json = readJson(mapper.toWebSocketMessage(event));

        assertThat(json.get("finalPrice").isNull()).isTrue();
        assertThat(json.get("winnerId").isNull()).isTrue();
    }

    @Test
    void OUTBID_newHighestBid_fallback을_적용한다() throws Exception {
        NotificationEvent event = baseEvent(OUTBID, "auction-4")
                .setTargetUserId("user-prev")
                .setPayload(Map.of("newHighestBid", "900000"))
                .build();

        JsonNode json = readJson(mapper.toWebSocketMessage(event));

        assertThat(json.get("type").asText()).isEqualTo(OUTBID);
        assertThat(json.get("currentPrice").asLong()).isEqualTo(900_000L);
    }

    @Test
    void BID_REJECTED_필드를_매핑한다() throws Exception {
        NotificationEvent event = baseEvent(BID_REJECTED, "auction-5")
                .setTargetUserId("user-bidder")
                .setPayload(Map.of("rejectedPrice", "100000", "reason", "PRICE_TOO_LOW"))
                .build();

        JsonNode json = readJson(mapper.toWebSocketMessage(event));

        assertThat(json.get("type").asText()).isEqualTo(BID_REJECTED);
        assertThat(json.get("rejectedPrice").asLong()).isEqualTo(100_000L);
        assertThat(json.get("reason").asText()).isEqualTo("PRICE_TOO_LOW");
    }

    @Test
    void 지원하지_않는_타입은_예외를_던진다() {
        NotificationEvent event = baseEvent("UNKNOWN", "auction-x").build();

        assertThatThrownBy(() -> mapper.toWebSocketMessage(event))
                .isInstanceOf(NotificationMappingException.class)
                .hasMessageContaining("UNKNOWN");
    }

    private static NotificationEvent.Builder baseEvent(String type, String auctionId) {
        return NotificationEventTestSupport.baseBuilder(type, auctionId).setEventId("evt-1");
    }

    private JsonNode readJson(String json) throws Exception {
        return objectMapper.readTree(json);
    }
}
