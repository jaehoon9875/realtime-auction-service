package com.jaehoon.user.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * application.yml의 app.security 프리픽스로부터 보안 설정값을 바인딩한다.
 */
@ConfigurationProperties(prefix = "app.security")
public class SecurityProperties {

    /** 인증 없이 접근 가능한 공개 엔드포인트 목록 */
    private final List<String> publicEndpoints;

    // Refresh Token을 Bearer 헤더로 수신하므로 Access Token 검증에서 제외해야 하는 경로
    private final String refreshEndpoint;

    public SecurityProperties(List<String> publicEndpoints, String refreshEndpoint) {
        this.publicEndpoints = publicEndpoints != null ? publicEndpoints : List.of();
        this.refreshEndpoint = refreshEndpoint != null ? refreshEndpoint : "/users/refresh";
    }

    public List<String> getPublicEndpoints() {
        return publicEndpoints;
    }

    public String getRefreshEndpoint() {
        return refreshEndpoint;
    }
}
