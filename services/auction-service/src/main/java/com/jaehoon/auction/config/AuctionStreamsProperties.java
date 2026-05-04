package com.jaehoon.auction.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.auction-streams")
public record AuctionStreamsProperties(
        /** auction-streams 베이스 URL (`application.yml`의 `base-url`과 매핑) */
        String baseUrl,
        /** TCP 연결 수립 제한(ms) */
        int connectTimeoutMs,
        /** 응답 수신 제한(ms), Reactor Netty의 responseTimeout에 대응 */
        int readTimeoutMs) {

    /**
     * 기본값은 {@code application.yml}의 {@code ${ENV:기본값}} 에만 둔다. Java 에서 리터럴로 보정하지 않는다.
     */
    public AuctionStreamsProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException(
                    "app.auction-streams.base-url 이 비어 있습니다. application.yml 또는 환경변수 AUCTION_STREAMS_BASE_URL 을 설정하세요.");
        }
        if (connectTimeoutMs <= 0 || readTimeoutMs <= 0) {
            throw new IllegalStateException(
                    "app.auction-streams 연결/읽기 타임아웃(ms)은 0보다 커야 합니다. application.yml 의 "
                            + "connect-timeout-ms, read-timeout-ms 를 확인하세요.");
        }
    }
}
