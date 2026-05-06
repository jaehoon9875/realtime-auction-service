package com.jaehoon.auction.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.jaehoon.auction.entity.Auction;
import com.jaehoon.auction.entity.AuctionStatus;
import com.jaehoon.auction.outbox.OutboxEventPublisher;
import com.jaehoon.auction.repository.AuctionRepository;
import com.jaehoon.auction.service.AuctionLifecycleTxHelper;

@ExtendWith(MockitoExtension.class)
class AuctionLifecycleTxHelperTest {

    @Mock
    AuctionRepository auctionRepository;

    @Mock
    OutboxEventPublisher outboxEventPublisher;

    @InjectMocks
    AuctionLifecycleTxHelper txHelper;

    // ─────────────────────────── activateOne ───────────────────────────

    @Test
    void activateOne_PENDING이고_시작시각_지났으면_ONGOING으로_전환한다() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        Auction auction = Auction.builder()
                .sellerId(UUID.randomUUID())
                .title("예약")
                .startPrice(1_000L)
                .startsAt(now.minus(1, ChronoUnit.MINUTES))
                .endsAt(now.plus(1, ChronoUnit.DAYS))
                .status(AuctionStatus.PENDING)
                .build();
        when(auctionRepository.findByIdForUpdate(id)).thenReturn(Optional.of(auction));

        txHelper.activateOne(id, now);

        assertThat(auction.getStatus()).isEqualTo(AuctionStatus.ONGOING);
        verify(outboxEventPublisher).publish(auction, "AUCTION_STATUS_CHANGED");
    }

    @Test
    void activateOne_시작시각이_아직_안_지났으면_상태_변경_없음() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        Auction auction = Auction.builder()
                .sellerId(UUID.randomUUID())
                .title("예약")
                .startPrice(1_000L)
                .startsAt(now.plus(1, ChronoUnit.HOURS))
                .endsAt(now.plus(1, ChronoUnit.DAYS))
                .status(AuctionStatus.PENDING)
                .build();
        when(auctionRepository.findByIdForUpdate(id)).thenReturn(Optional.of(auction));

        txHelper.activateOne(id, now);

        assertThat(auction.getStatus()).isEqualTo(AuctionStatus.PENDING);
        verify(outboxEventPublisher, never()).publish(any(), any());
    }

    // ─────────────────────────── closeOne ───────────────────────────

    @Test
    void closeOne_ONGOING이고_endsAt_지났으면_CLOSED로_전환한다() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        Auction auction = Auction.builder()
                .sellerId(UUID.randomUUID())
                .title("진행중")
                .startPrice(1_000L)
                .startsAt(now.minus(2, ChronoUnit.HOURS))
                .endsAt(now.minus(1, ChronoUnit.MINUTES))
                .status(AuctionStatus.ONGOING)
                .build();
        when(auctionRepository.findByIdForUpdate(id)).thenReturn(Optional.of(auction));

        txHelper.closeOne(id, now);

        assertThat(auction.getStatus()).isEqualTo(AuctionStatus.CLOSED);
        verify(outboxEventPublisher).publish(auction, "AUCTION_STATUS_CHANGED");
    }

    @Test
    void closeOne_endsAt가_아직_안_지났으면_그대로_ONGOING이다() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        Auction auction = Auction.builder()
                .sellerId(UUID.randomUUID())
                .title("진행중")
                .startPrice(1_000L)
                .startsAt(now.minus(2, ChronoUnit.HOURS))
                .endsAt(now.plus(1, ChronoUnit.HOURS))
                .status(AuctionStatus.ONGOING)
                .build();
        when(auctionRepository.findByIdForUpdate(id)).thenReturn(Optional.of(auction));

        txHelper.closeOne(id, now);

        assertThat(auction.getStatus()).isEqualTo(AuctionStatus.ONGOING);
        verify(outboxEventPublisher, never()).publish(any(), any());
    }
}
