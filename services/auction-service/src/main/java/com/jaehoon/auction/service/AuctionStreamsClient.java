package com.jaehoon.auction.service;

import java.util.UUID;

import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * auction-streams Interactive Queries REST API 클라이언트.
 * Circuit Breaker 적용: auction-streams 장애 시 currentPrice = null 로 fallback.
 * M5 이전에는 실제 State Store 가 없으므로 항상 fallback 이 반환된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionStreamsClient {

    private static final String CIRCUIT_BREAKER_NAME = "auction-streams";

    private final RestClient auctionStreamsRestClient;

    @SuppressWarnings("rawtypes")
    private final CircuitBreakerFactory circuitBreakerFactory;

    /**
     * 경매 ID 기준 현재 최고가를 State Store 에서 조회한다.
     * 장애 또는 M5 이전 미구현 상태이면 null 을 반환한다.
     *
     * @param auctionId 경매 ID
     * @return 현재 최고가(원), 조회 불가 시 null
     */
    @SuppressWarnings("unchecked")
    public Long getCurrentPrice(UUID auctionId) {
        return (Long) circuitBreakerFactory
                .create(CIRCUIT_BREAKER_NAME)
                .run(
                        () -> fetchCurrentPrice(auctionId),
                        throwable -> {
                            log.warn("auction-streams 현재가 조회 실패 (auctionId={}): {}", auctionId, throwable.getMessage());
                            return null;
                        }
                );
    }

    private Long fetchCurrentPrice(UUID auctionId) {
        try {
            return auctionStreamsRestClient.get()
                    .uri("/state/auctions/{auctionId}/current-price", auctionId)
                    .retrieve()
                    .body(Long.class);
        } catch (HttpClientErrorException.NotFound e) {
            // 아직 입찰이 없는 경매 → null 반환 (서비스 장애 아님, Circuit Breaker 실패로 미집계)
            return null;
        }
    }
}
