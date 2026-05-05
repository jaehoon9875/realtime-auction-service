package com.jaehoon.auction.scheduler;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.jaehoon.auction.service.AuctionService;

import lombok.RequiredArgsConstructor;

/**
 * `endsAt`이 지난 진행 중(ONGOING) 경매를 CLOSED로 전환한다.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.auction.schedule.enabled", havingValue = "true", matchIfMissing = true)
public class AuctionEndScheduler {

    private final AuctionService auctionService;

    @Scheduled(fixedDelayString = "${app.auction.schedule.ongoing-to-closed-ms:30000}")
    public void closeOverdueAuctions() {
        auctionService.closeOverdueAuctions();
    }
}
