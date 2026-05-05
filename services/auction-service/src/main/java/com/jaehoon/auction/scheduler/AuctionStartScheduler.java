package com.jaehoon.auction.scheduler;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.jaehoon.auction.service.AuctionService;

import lombok.RequiredArgsConstructor;

/**
 * 예약 경매(PENDING) 중 시작 시각이 도래한 건을 ONGOING 으로 전환한다.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.auction.schedule.enabled", havingValue = "true", matchIfMissing = true)
public class AuctionStartScheduler {

    private final AuctionService auctionService;

    @Scheduled(fixedDelayString = "${app.auction.schedule.pending-to-ongoing-ms:20000}")
    public void activatePendingAuctions() {
        auctionService.activateDueAuctions();
    }
}
