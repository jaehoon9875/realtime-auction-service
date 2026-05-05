package com.jaehoon.bid.service;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.HttpClientErrorException;

import com.jaehoon.bid.exception.ExternalServiceException;

import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Bid 검증용 경매 메타데이터(status, endsAt, startPrice) 조회 클라이언트.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionServiceClient {

    private static final String CIRCUIT_BREAKER_NAME = "auction-service";
    private static final String RETRY_NAME = "auction-service";

    @Qualifier("bidServiceRestClient")
    private final RestClient auctionServiceRestClient;
    private final CircuitBreakerFactory<?, ?> circuitBreakerFactory;
    private final RetryRegistry retryRegistry;

    public AuctionSnapshot getAuction(UUID auctionId) {
        Retry retry = retryRegistry.retry(RETRY_NAME);
        return circuitBreakerFactory
                .create(CIRCUIT_BREAKER_NAME)
                .run(
                        Retry.decorateSupplier(retry, () -> fetchAuction(auctionId))::get,
                        throwable -> {
                            log.warn("auction-service 조회 실패 (auctionId={}): {}", auctionId, throwable.getMessage());
                            throw new ExternalServiceException("auction-service 조회에 실패했습니다.");
                        });
    }

    private AuctionSnapshot fetchAuction(UUID auctionId) {
        try {
            return auctionServiceRestClient.get()
                    .uri("/auctions/{id}", auctionId)
                    .retrieve()
                    .body(AuctionSnapshot.class);
        } catch (HttpClientErrorException.NotFound e) {
            // 경매 미존재는 의존성 장애가 아니라 도메인 404 케이스이므로 null 로 전달한다.
            return null;
        } catch (RestClientResponseException e) {
            // 4xx/5xx 응답 본문이 있으면 원인 파악을 위해 상태코드를 로그로 남긴다.
            log.warn("auction-service 응답 오류 (auctionId={}, status={}): {}", auctionId, e.getStatusCode(), e.getMessage());
            throw e;
        } catch (RuntimeException e) {
            log.warn("auction-service 호출 예외 (auctionId={}): {}", auctionId, e.getMessage());
            throw e;
        }
    }

    public record AuctionSnapshot(
            UUID id,
            String status,
            Long startPrice,
            LocalDateTime endsAt) {
    }
}
