package com.jaehoon.bid.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.jaehoon.bid.dto.BidResponse;
import com.jaehoon.bid.entity.Bid;
import com.jaehoon.bid.entity.OutboxEvent;
import com.jaehoon.bid.exception.AuctionNotFoundException;
import com.jaehoon.bid.exception.BadRequestException;
import com.jaehoon.bid.exception.ExternalServiceException;
import com.jaehoon.bid.repository.BidRepository;
import com.jaehoon.bid.repository.OutboxEventRepository;
import com.jaehoon.bid.service.AuctionServiceClient;
import com.jaehoon.bid.service.AuctionServiceClient.AuctionSnapshot;
import com.jaehoon.bid.service.AuctionStreamsClient;
import com.jaehoon.bid.service.BidService;

@ExtendWith(MockitoExtension.class)
class BidServiceTest {

    @Mock
    BidRepository bidRepository;

    @Mock
    OutboxEventRepository outboxEventRepository;

    @Mock
    AuctionServiceClient auctionServiceClient;

    @Mock
    AuctionStreamsClient auctionStreamsClient;

    @InjectMocks
    BidService bidService;

    @Captor
    ArgumentCaptor<Bid> bidCaptor;

    @Captor
    ArgumentCaptor<OutboxEvent> outboxCaptor;

    @Test
    void placeBid_정상_입찰이면_bid와_outbox가_저장된다() {
        UUID bidderId = UUID.randomUUID();
        UUID auctionId = UUID.randomUUID();
        LocalDateTime nowUtc = LocalDateTime.now(ZoneOffset.UTC);

        when(auctionServiceClient.getAuction(auctionId))
                .thenReturn(new AuctionSnapshot(auctionId, "ONGOING", 10_000L, nowUtc.plusMinutes(10)));
        when(auctionStreamsClient.getCurrentPrice(auctionId)).thenReturn(11_000L);
        when(bidRepository.save(any(Bid.class))).thenAnswer(invocation -> {
            Bid input = invocation.getArgument(0);
            Bid saved = Bid.builder()
                    .auctionId(input.getAuctionId())
                    .bidderId(input.getBidderId())
                    .amount(input.getAmount())
                    .status(input.getStatus())
                    .build();
            // 단위 테스트에서는 JPA lifecycle이 동작하지 않으므로 save 결과 엔티티를 수동 보정한다.
            setField(saved, "id", UUID.randomUUID());
            setField(saved, "placedAt", nowUtc);
            return saved;
        });

        BidResponse response = bidService.placeBid(bidderId, auctionId, 12_000L);

        verify(bidRepository).save(bidCaptor.capture());
        verify(outboxEventRepository).save(outboxCaptor.capture());

        assertThat(response.auctionId()).isEqualTo(auctionId);
        assertThat(response.bidderId()).isEqualTo(bidderId);
        assertThat(bidCaptor.getValue().getAmount()).isEqualTo(12_000L);

        OutboxEvent outbox = outboxCaptor.getValue();
        assertThat(outbox.getAggregateType()).isEqualTo("BID");
        assertThat(outbox.getEventType()).isEqualTo("BID_PLACED");
        Map<String, Object> payload = outbox.getPayload();
        assertThat(payload).containsKeys("eventId", "eventType", "bidId", "auctionId", "bidderId", "amount", "occurredAt");
    }

    @Test
    void placeBid_경매가_없으면_404예외를_던진다() {
        UUID auctionId = UUID.randomUUID();
        when(auctionServiceClient.getAuction(auctionId)).thenReturn(null);

        assertThatThrownBy(() -> bidService.placeBid(UUID.randomUUID(), auctionId, 10_000L))
                .isInstanceOf(AuctionNotFoundException.class);

        verify(bidRepository, never()).save(any());
        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    void placeBid_경매상태가_ONGOING이_아니면_400예외를_던진다() {
        UUID auctionId = UUID.randomUUID();
        when(auctionServiceClient.getAuction(auctionId))
                .thenReturn(new AuctionSnapshot(auctionId, "CLOSED", 10_000L, LocalDateTime.now(ZoneOffset.UTC).plusMinutes(1)));

        assertThatThrownBy(() -> bidService.placeBid(UUID.randomUUID(), auctionId, 11_000L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("진행 중인 경매가 아닙니다.");
    }

    @Test
    void placeBid_마감시각이_지났으면_400예외를_던진다() {
        UUID auctionId = UUID.randomUUID();
        when(auctionServiceClient.getAuction(auctionId))
                .thenReturn(new AuctionSnapshot(auctionId, "ONGOING", 10_000L, LocalDateTime.now(ZoneOffset.UTC).minusSeconds(1)));

        assertThatThrownBy(() -> bidService.placeBid(UUID.randomUUID(), auctionId, 11_000L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("마감된 경매입니다.");
    }

    @Test
    void placeBid_첫입찰에서_시작가_이하면_400예외를_던진다() {
        UUID auctionId = UUID.randomUUID();
        when(auctionServiceClient.getAuction(auctionId))
                .thenReturn(new AuctionSnapshot(auctionId, "ONGOING", 10_000L, LocalDateTime.now(ZoneOffset.UTC).plusMinutes(5)));
        when(auctionStreamsClient.getCurrentPrice(auctionId)).thenReturn(null);

        assertThatThrownBy(() -> bidService.placeBid(UUID.randomUUID(), auctionId, 10_000L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("시작가보다 높아야 합니다.");
    }

    @Test
    void placeBid_현재최고가_이하면_400예외를_던진다() {
        UUID auctionId = UUID.randomUUID();
        when(auctionServiceClient.getAuction(auctionId))
                .thenReturn(new AuctionSnapshot(auctionId, "ONGOING", 10_000L, LocalDateTime.now(ZoneOffset.UTC).plusMinutes(5)));
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
                .thenReturn(new AuctionSnapshot(auctionId, "ONGOING", 10_000L, LocalDateTime.now(ZoneOffset.UTC).plusMinutes(5)));
        when(auctionStreamsClient.getCurrentPrice(auctionId))
                .thenThrow(new ExternalServiceException("auction-streams 조회에 실패했습니다."));

        assertThatThrownBy(() -> bidService.placeBid(UUID.randomUUID(), auctionId, 11_000L))
                .isInstanceOf(ExternalServiceException.class);
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(fieldName + " 필드 설정에 실패했습니다.", e);
        }
    }
}
