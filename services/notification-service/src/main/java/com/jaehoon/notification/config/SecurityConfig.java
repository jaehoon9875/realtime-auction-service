package com.jaehoon.notification.config;

import com.jaehoon.notification.security.QueryParamBearerTokenConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * WebFlux JWT Resource Server 보안 필터 체인을 구성한다.
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    // Kubernetes liveness/readiness 프로브는 인증 없이 접근 허용
    private static final String[] HEALTH_ENDPOINTS = { "/actuator/health", "/actuator/health/**" };

    /**
     * WebSocket 핸드셰이크 및 HTTP 요청에 JWT 인증을 적용한다.
     */
    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                // Bearer 토큰 기반 인증이므로 쿠키를 사용하지 않아 CSRF 방어 불필요
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers(HEALTH_ENDPOINTS).permitAll()
                        // WebSocket 핸드셰이크 시 JWT 인증 수행 (업그레이드 전 401 반환)
                        .pathMatchers("/ws/**").authenticated()
                        .anyExchange().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        // Authorization 헤더 우선, 없으면 ?token= 쿼리 파라미터에서 추출 (WebSocket 핸드셰이크용)
                        .bearerTokenConverter(new QueryParamBearerTokenConverter())
                        // JWK Set URI에서 공개키를 가져와 JWT 서명·만료 검증
                        .jwt(Customizer.withDefaults()))
                .build();
    }
}
