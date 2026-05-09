package com.jaehoon.streams.auction.web;

import com.jaehoon.streams.auction.service.StateQueryService;
import com.jaehoon.streams.auction.web.dto.AuctionStatusResponse;
import com.jaehoon.streams.auction.web.dto.HighestBidResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Kafka Streams State Store 조회용 REST (Interactive Query).
 * 멀티 인스턴스 환경에서는 {@link StateQueryService}가 담당 peer로 위임할 수 있다.
 */
@RestController
@RequiredArgsConstructor
public class StateQueryController {

    private final StateQueryService stateQueryService;

    /** 경매별 최고 입찰 스냅샷을 반환한다. */
    @GetMapping("/state/auctions/{auctionId}/highest-bid")
    public HighestBidResponse highestBid(@PathVariable String auctionId) {
        return stateQueryService.getHighestBid(auctionId);
    }

    /** 메타데이터 기준으로 경매 진행 중 여부({@code active})와 {@code endsAt}을 반환한다. */
    @GetMapping("/state/auctions/{auctionId}/status")
    public AuctionStatusResponse status(@PathVariable String auctionId) {
        return stateQueryService.getStatus(auctionId);
    }
}
