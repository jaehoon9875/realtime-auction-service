package com.jaehoon.auction.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import com.jaehoon.auction.dto.AuctionResponse;
import com.jaehoon.auction.dto.CreateAuctionRequest;
import com.jaehoon.auction.entity.Auction;
import com.jaehoon.auction.entity.AuctionStatus;
import com.jaehoon.auction.exception.AuctionNotFoundException;
import com.jaehoon.auction.exception.ForbiddenException;
import com.jaehoon.auction.outbox.OutboxEventPublisher;
import com.jaehoon.auction.repository.AuctionRepository;
import com.jaehoon.auction.service.AuctionService;
import com.jaehoon.auction.service.AuctionStreamsClient;

@ExtendWith(MockitoExtension.class)
class AuctionServiceTest {

    @Mock
    AuctionRepository auctionRepository;

    @Mock
    OutboxEventPublisher outboxEventPublisher;

    @Mock
    AuctionStreamsClient auctionStreamsClient;

    @InjectMocks
    AuctionService auctionService;

    // ─────────────────────────── createAuction ───────────────────────────

    @Test
    void createAuction_경매와_아웃박스_이벤트가_함께_저장된다() {
        // given
        UUID sellerId = UUID.randomUUID();
        CreateAuctionRequest request = new CreateAuctionRequest(
                "테스트 경매", "설명", 10_000L, LocalDateTime.now().plusDays(1));

        // when
        AuctionResponse response = auctionService.createAuction(request, sellerId);

        // then — AuctionRepository.save + OutboxEventPublisher.publish 모두 호출 확인
        verify(auctionRepository).save(any(Auction.class));
        verify(outboxEventPublisher).publish(any(Auction.class), eq("AUCTION_CREATED"));
        assertThat(response.sellerId()).isEqualTo(sellerId);
        assertThat(response.currentPrice()).isNull(); // 생성 시점엔 State Store 조회 불필요
    }

    @Test
    void createAuction_아웃박스_발행_없이_경매만_저장되는_경우는_없다() {
        // given
        UUID sellerId = UUID.randomUUID();
        CreateAuctionRequest request = new CreateAuctionRequest(
                "부분 저장 방지 검증", null, 1_000L, LocalDateTime.now().plusDays(1));

        // when
        auctionService.createAuction(request, sellerId);

        // then — 저장과 발행은 반드시 쌍으로 호출 (원자성 의도 표현)
        verify(auctionRepository).save(any(Auction.class));
        verify(outboxEventPublisher).publish(any(Auction.class), any());
    }

    // ─────────────────────────── updateStatus ───────────────────────────

    @Test
    void updateStatus_판매자_본인이면_상태가_변경된다() {
        // given
        UUID auctionId = UUID.randomUUID();
        UUID sellerId = UUID.randomUUID();
        Auction auction = buildAuction(sellerId);

        when(auctionRepository.findById(auctionId)).thenReturn(Optional.of(auction));

        // when
        AuctionResponse response = auctionService.updateStatus(auctionId, AuctionStatus.ACTIVE, sellerId);

        // then
        assertThat(response.status()).isEqualTo(AuctionStatus.ACTIVE);
        verify(outboxEventPublisher).publish(eq(auction), eq("AUCTION_STATUS_CHANGED"));
    }

    @Test
    void updateStatus_PENDING에서_ACTIVE로_전이된다() {
        // given
        UUID auctionId = UUID.randomUUID();
        UUID sellerId = UUID.randomUUID();
        Auction auction = buildAuction(sellerId);
        when(auctionRepository.findById(auctionId)).thenReturn(Optional.of(auction));

        // when
        auctionService.updateStatus(auctionId, AuctionStatus.ACTIVE, sellerId);

        // then
        assertThat(auction.getStatus()).isEqualTo(AuctionStatus.ACTIVE);
    }

    @Test
    void updateStatus_ACTIVE에서_CLOSED로_전이된다() {
        // given
        UUID auctionId = UUID.randomUUID();
        UUID sellerId = UUID.randomUUID();
        Auction auction = buildAuction(sellerId);
        auction.changeStatus(AuctionStatus.ACTIVE); // 사전 상태 설정

        when(auctionRepository.findById(auctionId)).thenReturn(Optional.of(auction));

        // when
        auctionService.updateStatus(auctionId, AuctionStatus.CLOSED, sellerId);

        // then
        assertThat(auction.getStatus()).isEqualTo(AuctionStatus.CLOSED);
        verify(outboxEventPublisher).publish(eq(auction), eq("AUCTION_STATUS_CHANGED"));
    }

    @Test
    void updateStatus_판매자가_아니면_ForbiddenException을_던진다() {
        // given
        UUID auctionId = UUID.randomUUID();
        UUID sellerId = UUID.randomUUID();
        UUID otherId = UUID.randomUUID();
        Auction auction = buildAuction(sellerId);

        when(auctionRepository.findById(auctionId)).thenReturn(Optional.of(auction));

        // when & then
        assertThatThrownBy(() -> auctionService.updateStatus(auctionId, AuctionStatus.ACTIVE, otherId))
                .isInstanceOf(ForbiddenException.class);

        // 권한 오류 시 아웃박스 이벤트가 발행되지 않아야 함
        verify(outboxEventPublisher, never()).publish(any(), any());
    }

    @Test
    void updateStatus_requesterId가_null이면_권한_검증을_생략한다() {
        // given — 시스템 내부 호출 케이스 (requesterId = null)
        UUID auctionId = UUID.randomUUID();
        UUID sellerId = UUID.randomUUID();
        Auction auction = buildAuction(sellerId);

        when(auctionRepository.findById(auctionId)).thenReturn(Optional.of(auction));

        // when & then — 예외 없이 성공
        AuctionResponse response = auctionService.updateStatus(auctionId, AuctionStatus.CLOSED, null);
        assertThat(response.status()).isEqualTo(AuctionStatus.CLOSED);
    }

    @Test
    void updateStatus_경매가_없으면_AuctionNotFoundException을_던진다() {
        // given
        UUID missingId = UUID.randomUUID();
        when(auctionRepository.findById(missingId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> auctionService.updateStatus(missingId, AuctionStatus.ACTIVE, UUID.randomUUID()))
                .isInstanceOf(AuctionNotFoundException.class);
    }

    // ─────────────────────────── getAuction ───────────────────────────

    @Test
    void getAuction_currentPrice를_StateStore에서_조회한다() {
        // given
        UUID auctionId = UUID.randomUUID();
        Auction auction = buildAuction(UUID.randomUUID());
        when(auctionRepository.findById(auctionId)).thenReturn(Optional.of(auction));
        when(auctionStreamsClient.getCurrentPrice(auctionId)).thenReturn(15_000L);

        // when
        AuctionResponse response = auctionService.getAuction(auctionId);

        // then
        assertThat(response.currentPrice()).isEqualTo(15_000L);
        verify(auctionStreamsClient).getCurrentPrice(auctionId);
    }

    @Test
    void getAuction_StateStore_장애시_currentPrice가_null이다() {
        // given — Circuit Breaker fallback 시나리오
        UUID auctionId = UUID.randomUUID();
        Auction auction = buildAuction(UUID.randomUUID());
        when(auctionRepository.findById(auctionId)).thenReturn(Optional.of(auction));
        when(auctionStreamsClient.getCurrentPrice(auctionId)).thenReturn(null);

        // when
        AuctionResponse response = auctionService.getAuction(auctionId);

        // then — currentPrice null이어도 나머지 필드는 정상 반환
        assertThat(response.currentPrice()).isNull();
        assertThat(response.sellerId()).isEqualTo(auction.getSellerId());
    }

    @Test
    void getAuction_경매가_없으면_AuctionNotFoundException을_던진다() {
        // given
        UUID missingId = UUID.randomUUID();
        when(auctionRepository.findById(missingId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> auctionService.getAuction(missingId))
                .isInstanceOf(AuctionNotFoundException.class);
    }

    // ─────────────────────────── getAuctions ───────────────────────────

    @Test
    void getAuctions_목록_조회_시_currentPrice는_null이다() {
        // given — 목록 조회는 State Store를 호출하지 않음
        Auction a1 = buildAuction(UUID.randomUUID());
        Auction a2 = buildAuction(UUID.randomUUID());
        PageRequest pageable = PageRequest.of(0, 10);
        when(auctionRepository.findAllByOrderByCreatedAtDesc(pageable))
                .thenReturn(new PageImpl<>(List.of(a1, a2)));

        // when
        var page = auctionService.getAuctions(pageable);

        // then
        assertThat(page.getContent()).hasSize(2);
        assertThat(page.getContent()).allSatisfy(r -> assertThat(r.currentPrice()).isNull());
        verify(auctionStreamsClient, never()).getCurrentPrice(any());
    }

    // ─────────────────────────── 헬퍼 ───────────────────────────

    /** @PrePersist 없이 sellerId만 세팅한 최소 Auction 객체 */
    private Auction buildAuction(UUID sellerId) {
        return Auction.builder()
                .sellerId(sellerId)
                .title("테스트 경매")
                .startPrice(1_000L)
                .endsAt(LocalDateTime.now().plusDays(1))
                .build();
    }
}
