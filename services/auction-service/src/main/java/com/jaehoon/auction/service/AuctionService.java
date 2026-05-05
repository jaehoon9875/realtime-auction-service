package com.jaehoon.auction.service;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jaehoon.auction.dto.AuctionResponse;
import com.jaehoon.auction.dto.CreateAuctionRequest;
import com.jaehoon.auction.entity.Auction;
import com.jaehoon.auction.entity.AuctionStatus;
import com.jaehoon.auction.exception.AuctionNotFoundException;
import com.jaehoon.auction.exception.BadRequestException;
import com.jaehoon.auction.exception.ForbiddenException;
import com.jaehoon.auction.outbox.OutboxEventPublisher;
import com.jaehoon.auction.repository.AuctionRepository;

import lombok.RequiredArgsConstructor;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AuctionService {

    /** 스케줄러 한 번에 처리할 예약 경매 후보 최대 건수 */
    private static final int PENDING_ACTIVATION_BATCH_SIZE = 100;

    /** 스케줄러 한 번에 처리할 마감 대기(ONGOING·endsAt 경과) 최대 건수 */
    private static final int CLOSE_OVERDUE_BATCH_SIZE = 100;

    private final AuctionRepository auctionRepository;
    private final OutboxEventPublisher outboxEventPublisher;
    private final AuctionStreamsClient auctionStreamsClient;

    /**
     * 경매 생성.
     * 트랜잭션 경계: auctions INSERT + outbox_events INSERT 를 하나의 커밋으로 묶는다.
     * Debezium 이 outbox_events WAL 을 읽어 auction-events 토픽으로 전달한다.
     */
    @Transactional
    public AuctionResponse createAuction(CreateAuctionRequest request, UUID sellerId) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime resolvedStartsAt = request.startsAt() != null ? request.startsAt() : now;
        if (!resolvedStartsAt.isBefore(request.endsAt())) {
            throw new BadRequestException("마감 시각은 시작 시각보다 이후여야 합니다.");
        }
        // 시작 시각이 아직 안 왔으면 PENDING, 이미 도래(또는 생략으로 즉시 시작)면 ONGOING
        AuctionStatus initialStatus = resolvedStartsAt.isAfter(now)
                ? AuctionStatus.PENDING
                : AuctionStatus.ONGOING;

        Auction auction = Auction.builder()
                .sellerId(sellerId)
                .title(request.title())
                .description(request.description())
                .startPrice(request.startPrice())
                .startsAt(resolvedStartsAt)
                .endsAt(request.endsAt())
                .status(initialStatus)
                .build();
        auctionRepository.save(auction);

        outboxEventPublisher.publish(auction, "AUCTION_CREATED");

        return AuctionResponse.from(auction, null);
    }

    /**
     * 예약 경매: 시작 시각이 지난 PENDING 건을 ONGOING 으로 바꾼다 (스케줄러 전용).
     * 행 단위로 배타 락을 걸어 멀티 인스턴스에서 중복 전환을 줄인다.
     */
    @Transactional
    public void activateDueAuctions() {
        LocalDateTime now = LocalDateTime.now();
        var ids = auctionRepository.findIdsDuePendingAuctions(
                AuctionStatus.PENDING,
                now,
                PageRequest.of(0, PENDING_ACTIVATION_BATCH_SIZE));
        for (UUID id : ids) {
            activatePendingAuctionIfDue(id, now);
        }
    }

    private void activatePendingAuctionIfDue(UUID id, LocalDateTime now) {
        auctionRepository.findByIdForUpdate(id).ifPresent(auction -> {
            if (auction.getStatus() != AuctionStatus.PENDING) {
                return;
            }
            if (auction.getStartsAt().isAfter(now)) {
                return;
            }
            auction.changeStatus(AuctionStatus.ONGOING);
            outboxEventPublisher.publish(auction, "AUCTION_STATUS_CHANGED");
        });
    }

    /**
     * 시간 마감: `endsAt`이 지난 `ONGOING` 경매를 `CLOSED`로 바꾼다 (스케줄러 전용).
     * 행 단위 배타 락으로 멀티 인스턴스에서 중복 전환을 줄인다.
     */
    @Transactional
    public void closeOverdueAuctions() {
        LocalDateTime now = LocalDateTime.now();
        var ids = auctionRepository.findIdsOngoingPastEnd(
                AuctionStatus.ONGOING,
                now,
                PageRequest.of(0, CLOSE_OVERDUE_BATCH_SIZE));
        for (UUID id : ids) {
            closeOngoingAuctionIfOverdue(id, now);
        }
    }

    private void closeOngoingAuctionIfOverdue(UUID id, LocalDateTime now) {
        auctionRepository.findByIdForUpdate(id).ifPresent(auction -> {
            if (auction.getStatus() != AuctionStatus.ONGOING) {
                return;
            }
            if (auction.getEndsAt().isAfter(now)) {
                return;
            }
            auction.changeStatus(AuctionStatus.CLOSED);
            outboxEventPublisher.publish(auction, "AUCTION_STATUS_CHANGED");
        });
    }

    /**
     * 경매 단건 조회.
     * currentPrice 는 Kafka Streams State Store 에서 조회한다.
     * auction-streams 장애 시 Circuit Breaker 가 동작하여 null 을 반환한다.
     */
    public AuctionResponse getAuction(UUID auctionId) {
        Auction auction = auctionRepository.findById(auctionId)
                .orElseThrow(() -> new AuctionNotFoundException(auctionId));

        // State Store 조회 — 실패 시 null (CLAUDE.md: currentPrice 는 DB 에 저장하지 않음)
        Long currentPrice = auctionStreamsClient.getCurrentPrice(auctionId);

        return AuctionResponse.from(auction, currentPrice);
    }

    /**
     * 경매 목록 조회 (최근 생성 순, 페이징).
     * 목록에서는 currentPrice 를 일일이 조회하지 않고 null 로 반환한다.
     * 필요 시 클라이언트가 단건 조회로 currentPrice 를 추가 요청한다.
     */
    public Page<AuctionResponse> getAuctions(Pageable pageable) {
        return auctionRepository.findAllByOrderByCreatedAtDesc(pageable)
                .map(auction -> AuctionResponse.from(auction, null));
    }

    /**
     * 경매 상태 변경 (내부 API).
     * 트랜잭션 경계: auctions UPDATE + outbox_events INSERT 를 하나의 커밋으로 묶는다.
     *
     * @param auctionId   변경 대상 경매 ID
     * @param newStatus   변경할 상태 (ONGOING | CLOSED)
     * @param requesterId 요청자 ID. null 이면 권한 검증 생략 (시스템 내부 호출)
     */
    @Transactional
    public AuctionResponse updateStatus(UUID auctionId, AuctionStatus newStatus, UUID requesterId) {
        Auction auction = auctionRepository.findById(auctionId)
                .orElseThrow(() -> new AuctionNotFoundException(auctionId));

        // 판매자 본인만 상태 변경 가능 (requesterId 가 있을 때만 검증)
        if (requesterId != null && !auction.getSellerId().equals(requesterId)) {
            throw new ForbiddenException();
        }

        // 상태 전이 및 아웃박스 이벤트 발행
        auction.changeStatus(newStatus);
        outboxEventPublisher.publish(auction, "AUCTION_STATUS_CHANGED");

        return AuctionResponse.from(auction, null);
    }
}
