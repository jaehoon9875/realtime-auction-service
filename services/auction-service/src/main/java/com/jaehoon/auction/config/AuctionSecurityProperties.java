package com.jaehoon.auction.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Gateway가 붙여 보내는 내부 요청 토큰 검증용 설정.
 */
@ConfigurationProperties(prefix = "app.security")
public record AuctionSecurityProperties(
        /** 비교할 HTTP 헤더 이름 (Gateway AddRequestHeader와 동일해야 함) */
        String internalRequestHeaderName,
        /** 헤더 값과 일치해야 통과; 비어 있으면 검증 생략(로컬 단독 기동용) */
        String internalRequestSecret,
        /** 인증 없이 접근 가능한 공개 엔드포인트 목록 (Swagger UI 등) */
        List<String> publicEndpoints

) {
    public AuctionSecurityProperties {
        if (internalRequestHeaderName == null || internalRequestHeaderName.isBlank()) {
            internalRequestHeaderName = "X-Internal-Request-Token";
        }
        if (publicEndpoints == null) {
            publicEndpoints = List.of();
        }
    }
}
