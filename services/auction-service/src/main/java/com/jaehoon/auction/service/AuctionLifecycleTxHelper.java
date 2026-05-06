package com.jaehoon.auction.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.jaehoon.auction.entity.AuctionStatus;
import com.jaehoon.auction.outbox.OutboxEventPublisher;
import com.jaehoon.auction.repository.AuctionRepository;

import lombok.RequiredArgsConstructor;

/**
 * 경매 생명주기 상태 전이를 행 단위 독립 트랜잭션으로 처리한다.
 * REQUIRES_NEW가 AOP 프록시를 통해 동작하려면 AuctionService와 별도 빈이어야 한다.
 */
@Component
@RequiredArgsConstructor
public class AuctionLifecycleTxHelper {

    private final AuctionRepository auctionRepository;
    private final OutboxEventPublisher outboxEventPublisher;

    /** PENDING → ONGOING: 실패 시 해당 건만 롤백, 나머지 건에 영향 없음 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void activateOne(UUID id, Instant now) {
        auctionRepository.findByIdForUpdate(id).ifPresent(auction -> {
            if (auction.getStatus() != AuctionStatus.PENDING) return;
            if (auction.getStartsAt().isAfter(now)) return;
            auction.changeStatus(AuctionStatus.ONGOING);
            outboxEventPublisher.publish(auction, "AUCTION_STATUS_CHANGED");
        });
    }

    /** ONGOING → CLOSED: 실패 시 해당 건만 롤백, 나머지 건에 영향 없음 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void closeOne(UUID id, Instant now) {
        auctionRepository.findByIdForUpdate(id).ifPresent(auction -> {
            if (auction.getStatus() != AuctionStatus.ONGOING) return;
            if (auction.getEndsAt().isAfter(now)) return;
            auction.changeStatus(AuctionStatus.CLOSED);
            outboxEventPublisher.publish(auction, "AUCTION_STATUS_CHANGED");
        });
    }
}
