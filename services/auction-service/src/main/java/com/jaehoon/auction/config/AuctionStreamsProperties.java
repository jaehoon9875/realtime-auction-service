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

    public AuctionStreamsProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "http://localhost:8085";
        }
        if (connectTimeoutMs <= 0) {
            connectTimeoutMs = 2000;
        }
        if (readTimeoutMs <= 0) {
            readTimeoutMs = 3000;
        }
    }
}
