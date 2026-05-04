package com.jaehoon.auction.service;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jaehoon.auction.dto.AuctionResponse;
import com.jaehoon.auction.dto.CreateAuctionRequest;
import com.jaehoon.auction.entity.Auction;
import com.jaehoon.auction.entity.AuctionStatus;
import com.jaehoon.auction.exception.AuctionNotFoundException;
import com.jaehoon.auction.exception.ForbiddenException;
import com.jaehoon.auction.outbox.OutboxEventPublisher;
import com.jaehoon.auction.repository.AuctionRepository;

import lombok.RequiredArgsConstructor;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AuctionService {

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
        // 1. 경매 저장
        Auction auction = Auction.builder()
                .sellerId(sellerId)
                .title(request.title())
                .description(request.description())
                .startPrice(request.startPrice())
                .endsAt(request.endsAt())
                .build();
        auctionRepository.save(auction);

        // 2. 아웃박스 이벤트 저장 — 같은 트랜잭션 (직접 Kafka 발행 금지)
        outboxEventPublisher.publish(auction, "AUCTION_CREATED");

        // currentPrice 는 DB 에 없음. 생성 시점은 State Store 조회 불필요이므로 null 반환
        return AuctionResponse.from(auction, null);
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
     * @param newStatus   변경할 상태 (ACTIVE | CLOSED)
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
