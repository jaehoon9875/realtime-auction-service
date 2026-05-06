package com.jaehoon.bid.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.auction-streams")
public record BidStreamsProperties(
        String baseUrl,
        int connectTimeoutMs,
        int readTimeoutMs) {

    public BidStreamsProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException(
                    "app.auction-streams.base-url 이 비어 있습니다. application.yml 또는 AUCTION_STREAMS_BASE_URL 을 설정하세요.");
        }
        if (connectTimeoutMs <= 0 || readTimeoutMs <= 0) {
            throw new IllegalStateException(
                    "app.auction-streams 연결/읽기 타임아웃(ms)은 0보다 커야 합니다.");
        }
    }
}
