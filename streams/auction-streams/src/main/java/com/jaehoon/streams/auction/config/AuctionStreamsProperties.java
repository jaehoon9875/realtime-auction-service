package com.jaehoon.streams.auction.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * auction-streams 모듈 전용 설정.
 * 환경변수 주입 예시: PUNCTUATOR_INTERVAL_SECONDS=30
 */
@ConfigurationProperties(prefix = "auction.streams")
public record AuctionStreamsProperties(int punctuatorIntervalSeconds, int maxFailures) {

    public AuctionStreamsProperties {
        if (punctuatorIntervalSeconds <= 0) {
            throw new IllegalStateException(
                    "auction.streams.punctuator-interval-seconds 는 0보다 커야 합니다.");
        }
        if (maxFailures <= 0) {
            throw new IllegalStateException(
                    "auction.streams.max-failures 는 0보다 커야 합니다.");
        }
    }
}
