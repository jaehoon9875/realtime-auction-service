package com.jaehoon.bid.service;

import java.util.UUID;

import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import com.jaehoon.bid.exception.ExternalServiceException;

import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * State Store 현재 최고가 조회 클라이언트.
 * 장애 시 fallback 없이 즉시 503으로 실패시켜 정합성을 우선한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionStreamsClient {

    private static final String CIRCUIT_BREAKER_NAME = "auction-streams";
    private static final String RETRY_NAME = "auction-streams";

    @Qualifier("bidStreamsRestClient")
    private final RestClient auctionStreamsRestClient;
    private final CircuitBreakerFactory<?, ?> circuitBreakerFactory;
    private final RetryRegistry retryRegistry;

    public Long getCurrentPrice(UUID auctionId) {
        Retry retry = retryRegistry.retry(RETRY_NAME);
        return circuitBreakerFactory
                .create(CIRCUIT_BREAKER_NAME)
                .run(
                        Retry.decorateSupplier(retry, () -> fetchCurrentPrice(auctionId))::get,
                        throwable -> {
                            log.warn("auction-streams 조회 실패 (auctionId={}): {}", auctionId, throwable.getMessage());
                            throw new ExternalServiceException("auction-streams 조회에 실패했습니다.");
                        });
    }

    private Long fetchCurrentPrice(UUID auctionId) {
        try {
            return auctionStreamsRestClient.get()
                    .uri("/state/auctions/{id}/current-price", auctionId)
                    .retrieve()
                    .body(Long.class);
        } catch (HttpClientErrorException.NotFound e) {
            // 첫 입찰 이전에는 현재 최고가가 없을 수 있으므로 404를 정상 케이스(null)로 취급한다.
            return null;
        }
    }
}
