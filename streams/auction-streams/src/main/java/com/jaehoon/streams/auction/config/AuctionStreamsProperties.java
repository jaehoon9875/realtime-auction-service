package com.jaehoon.streams.auction.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * auction-streams 모듈 전용 설정(prefix {@code auction.streams}).
 * Spring Boot relaxed binding 환경변수 예:
 * {@code AUCTION_STREAMS_PUNCTUATOR_INTERVAL_SECONDS}, {@code AUCTION_STREAMS_MAX_FAILURES},
 * {@code AUCTION_STREAMS_IQ_PEER_CONNECT_TIMEOUT_MS}, {@code AUCTION_STREAMS_IQ_PEER_READ_TIMEOUT_MS}
 * — {@code application.yml}에서는 구 호환용 짧은 이름({@code PUNCTUATOR_INTERVAL_SECONDS} 등) fallback도 허용한다.
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
