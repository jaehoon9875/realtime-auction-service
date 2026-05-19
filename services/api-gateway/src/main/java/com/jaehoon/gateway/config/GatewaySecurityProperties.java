package com.jaehoon.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * application.yml의 app.security 프리픽스로부터 Gateway 보안 설정값을 바인딩한다.
 */
@ConfigurationProperties(prefix = "app.security")
public class GatewaySecurityProperties {

    // JWT 검증을 건너뛰는 공개 엔드포인트 (클라이언트 기준 경로, /api 접두사 포함)
    private final List<String> publicEndpoints;

    /**
     * GatewaySecurityProperties 생성자
     * 
     * @param publicEndpoints JWT 검증을 건너뛰는 공개 엔드포인트 목록
     */
    public GatewaySecurityProperties(List<String> publicEndpoints) {
        this.publicEndpoints = publicEndpoints != null ? publicEndpoints : List.of();
    }

    /**
     * JWT 검증을 건너뛰는 공개 엔드포인트 목록을 반환한다.
     * 
     * @return 공개 엔드포인트 목록
     */
    public List<String> getPublicEndpoints() {
        return publicEndpoints;
    }
}
