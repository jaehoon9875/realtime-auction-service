package com.jaehoon.bid.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Gateway가 붙여 보내는 내부 요청 토큰 검증용 설정.
 */
@ConfigurationProperties(prefix = "app.security")
public record BidSecurityProperties(
        String internalRequestHeaderName,
        String internalRequestSecret) {

    public BidSecurityProperties {
        if (internalRequestHeaderName == null || internalRequestHeaderName.isBlank()) {
            internalRequestHeaderName = "X-Internal-Request-Token";
        }
    }
}
