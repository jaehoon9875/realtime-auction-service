package com.jaehoon.bid.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.jaehoon.bid.dto.BidResponse;
import com.jaehoon.bid.entity.BidStatus;
import com.jaehoon.bid.exception.AuctionNotFoundException;
import com.jaehoon.bid.exception.BadRequestException;
import com.jaehoon.bid.exception.ExternalServiceException;
import com.jaehoon.bid.repository.BidRepository;
import com.jaehoon.bid.service.AuctionServiceClient;
import com.jaehoon.bid.service.AuctionServiceClient.AuctionSnapshot;
import com.jaehoon.bid.service.AuctionStreamsClient;
import com.jaehoon.bid.service.BidService;
import com.jaehoon.bid.service.BidTransactionService;

@ExtendWith(MockitoExtension.class)
class BidServiceTest {

    @Mock
    BidRepository bidRepository;

    @Mock
    AuctionServiceClient auctionServiceClient;

    @Mock
    AuctionStreamsClient auctionStreamsClient;

    @Mock
    BidTransactionService bidTransactionService;

    @InjectMocks
    BidService bidService;

    @Test
    void placeBid_정상_입찰이면_bidTransactionService에_위임한다() {
        UUID bidderId = UUID.randomUUID();
        UUID auctionId = UUID.randomUUID();
        Instant nowUtc = Instant.now();
        BidResponse expected = new BidResponse(UUID.randomUUID(), auctionId, bidderId, 12_000L, BidStatus.ACCEPTED, nowUtc);

        when(auctionServiceClient.getAuction(auctionId))
                .thenReturn(new AuctionSnapshot(auctionId, "ONGOING", 10_000L, nowUtc.plus(10, ChronoUnit.MINUTES)));
        when(auctionStreamsClient.getCurrentPrice(auctionId)).thenReturn(11_000L);
        when(bidTransactionService.saveBidWithOutbox(bidderId, auctionId, 12_000L)).thenReturn(expected);

        BidResponse response = bidService.placeBid(bidderId, auctionId, 12_000L);

        verify(bidTransactionService).saveBidWithOutbox(bidderId, auctionId, 12_000L);
        assertThat(response).isEqualTo(expected);
    }

    @Test
    void placeBid_경매가_없으면_404예외를_던진다() {
        UUID auctionId = UUID.randomUUID();
        when(auctionServiceClient.getAuction(auctionId)).thenReturn(null);

        assertThatThrownBy(() -> bidService.placeBid(UUID.randomUUID(), auctionId, 10_000L))
                .isInstanceOf(AuctionNotFoundException.class);

        verifyNoInteractions(bidTransactionService);
    }

    @Test
    void placeBid_경매상태가_ONGOING이_아니면_400예외를_던진다() {
        UUID auctionId = UUID.randomUUID();
        when(auctionServiceClient.getAuction(auctionId))
                .thenReturn(new AuctionSnapshot(auctionId, "CLOSED", 10_000L, Instant.now().plus(1, ChronoUnit.MINUTES)));

        assertThatThrownBy(() -> bidService.placeBid(UUID.randomUUID(), auctionId, 11_000L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("진행 중인 경매가 아닙니다.");
    }

    @Test
    void placeBid_마감시각이_지났으면_400예외를_던진다() {
        UUID auctionId = UUID.randomUUID();
        when(auctionServiceClient.getAuction(auctionId))
                .thenReturn(new AuctionSnapshot(auctionId, "ONGOING", 10_000L, Instant.now().minusSeconds(1)));

        assertThatThrownBy(() -> bidService.placeBid(UUID.randomUUID(), auctionId, 11_000L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("마감된 경매입니다.");
    }

    @Test
    void placeBid_첫입찰에서_시작가_이하면_400예외를_던진다() {
        UUID auctionId = UUID.randomUUID();
        when(auctionServiceClient.getAuction(auctionId))
                .thenReturn(new AuctionSnapshot(auctionId, "ONGOING", 10_000L, Instant.now().plus(5, ChronoUnit.MINUTES)));
        when(auctionStreamsClient.getCurrentPrice(auctionId)).thenReturn(null);

        assertThatThrownBy(() -> bidService.placeBid(UUID.randomUUID(), auctionId, 10_000L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("시작가보다 높아야 합니다.");
    }

    @Test
    void placeBid_현재최고가_이하면_400예외를_던진다() {
        UUID auctionId = UUID.randomUUID();
        when(auctionServiceClient.getAuction(auctionId))
                .thenReturn(new AuctionSnapshot(auctionId, "ONGOING", 10_000L, Instant.now().plus(5, ChronoUnit.MINUTES)));
        when(auctionStreamsClient.getCurrentPrice(auctionId)).thenReturn(13_000L);

        assertThatThrownBy(() -> bidService.placeBid(UUID.randomUUID(), auctionId, 13_000L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("현재 최고가보다 높아야 합니다.");
    }

    @Test
    void placeBid_auctionService_CB_open이면_503예외를_던진다() {
        UUID auctionId = UUID.randomUUID();
        when(auctionServiceClient.getAuction(auctionId))
                .thenThrow(new ExternalServiceException("auction-service 조회에 실패했습니다."));

        assertThatThrownBy(() -> bidService.placeBid(UUID.randomUUID(), auctionId, 11_000L))
                .isInstanceOf(ExternalServiceException.class);
    }

    @Test
    void placeBid_auctionStreams_CB_open이면_503예외를_던진다() {
        UUID auctionId = UUID.randomUUID();
        when(auctionServiceClient.getAuction(auctionId))
                .thenReturn(new AuctionSnapshot(auctionId, "ONGOING", 10_000L, Instant.now().plus(5, ChronoUnit.MINUTES)));
        when(auctionStreamsClient.getCurrentPrice(auctionId))
                .thenThrow(new ExternalServiceException("auction-streams 조회에 실패했습니다."));

        assertThatThrownBy(() -> bidService.placeBid(UUID.randomUUID(), auctionId, 11_000L))
                .isInstanceOf(ExternalServiceException.class);
    }
}
