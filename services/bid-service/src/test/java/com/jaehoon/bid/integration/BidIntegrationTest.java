package com.jaehoon.bid.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.jaehoon.bid.dto.BidResponse;
import com.jaehoon.bid.entity.OutboxEvent;
import com.jaehoon.bid.repository.BidRepository;
import com.jaehoon.bid.repository.OutboxEventRepository;
import com.jaehoon.bid.service.AuctionServiceClient;
import com.jaehoon.bid.service.AuctionServiceClient.AuctionSnapshot;
import com.jaehoon.bid.service.AuctionStreamsClient;
import com.jaehoon.bid.service.BidService;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
@ActiveProfiles("integration")
class BidIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    @MockitoBean
    AuctionServiceClient auctionServiceClient;

    @MockitoBean
    AuctionStreamsClient auctionStreamsClient;

    @Autowired
    BidService bidService;

    @Autowired
    BidRepository bidRepository;

    @Autowired
    OutboxEventRepository outboxEventRepository;

    @BeforeEach
    void cleanDatabase() {
        outboxEventRepository.deleteAll();
        bidRepository.deleteAll();
    }

    @Test
    void placeBid_성공시_outbox_events에_BID_PLACED가_저장된다() {
        UUID bidderId = UUID.randomUUID();
        UUID auctionId = UUID.randomUUID();

        doAnswer(invocation -> new AuctionSnapshot(
                auctionId,
                "ONGOING",
                10_000L,
                LocalDateTime.now(ZoneOffset.UTC).plusMinutes(10)))
                .when(auctionServiceClient).getAuction(auctionId);
        doAnswer(invocation -> 11_000L).when(auctionStreamsClient).getCurrentPrice(auctionId);

        BidResponse response = bidService.placeBid(bidderId, auctionId, 12_000L);

        assertThat(response.id()).isNotNull();
        assertThat(bidRepository.count()).isEqualTo(1);

        List<OutboxEvent> outboxEvents = outboxEventRepository.findAll();
        assertThat(outboxEvents).hasSize(1);
        assertThat(outboxEvents.get(0).getEventType()).isEqualTo("BID_PLACED");
        assertThat(outboxEvents.get(0).getAggregateType()).isEqualTo("BID");
        assertThat(outboxEvents.get(0).getPayload()).containsKeys("auctionId", "bidderId", "amount", "occurredAt");
    }

    @Test
    void 검증_실패_시_저장이_일어나지_않는다() {
        UUID auctionId = UUID.randomUUID();
        doAnswer(invocation -> new AuctionSnapshot(
                auctionId,
                "ONGOING",
                10_000L,
                LocalDateTime.now(ZoneOffset.UTC).plusMinutes(10)))
                .when(auctionServiceClient).getAuction(auctionId);
        doAnswer(invocation -> null).when(auctionStreamsClient).getCurrentPrice(auctionId);

        assertThatThrownBy(() -> bidService.placeBid(
                UUID.randomUUID(),
                auctionId,
                0L))
                .isInstanceOf(com.jaehoon.bid.exception.BadRequestException.class);

        // 검증 실패 예외가 발생하면 bids/outbox 모두 저장되면 안 된다.
        assertThat(bidRepository.count()).isZero();
        assertThat(outboxEventRepository.count()).isZero();
    }
}
