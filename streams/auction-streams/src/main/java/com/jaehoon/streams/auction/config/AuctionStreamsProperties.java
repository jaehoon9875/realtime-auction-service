package com.jaehoon.streams.auction.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * auction-streams 모듈 전용 설정.
 * 환경변수 예: {@code AUCTION_STREAMS_PUNCTUATOR_INTERVAL_SECONDS}, {@code AUCTION_STREAMS_IQ_PEER_CONNECT_TIMEOUT_MS}
 */
@ConfigurationProperties(prefix = "auction.streams")
public record AuctionStreamsProperties(
        int punctuatorIntervalSeconds,
        int maxFailures,
        /** 멀티 인스턴스 IQ peer {@link org.springframework.web.client.RestClient} TCP 연결 제한(ms) */
        int iqPeerConnectTimeoutMs,
        /** peer 응답 수신 제한(ms) */
        int iqPeerReadTimeoutMs) {

    public AuctionStreamsProperties {
        if (punctuatorIntervalSeconds <= 0) {
            throw new IllegalStateException(
                    "auction.streams.punctuator-interval-seconds 는 0보다 커야 합니다.");
        }
        if (maxFailures <= 0) {
            throw new IllegalStateException(
                    "auction.streams.max-failures 는 0보다 커야 합니다.");
        }
        if (iqPeerConnectTimeoutMs <= 0 || iqPeerReadTimeoutMs <= 0) {
            throw new IllegalStateException(
                    "auction.streams iq-peer-connect-timeout-ms / iq-peer-read-timeout-ms 는 0보다 커야 합니다.");
        }
    }
}
