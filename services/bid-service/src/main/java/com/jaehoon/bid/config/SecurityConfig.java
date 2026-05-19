package com.jaehoon.bid.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * JWT 기반 Stateless 인증 필터 체인을 구성한다.
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(BidSecurityProperties.class)
@RequiredArgsConstructor
public class SecurityConfig {

    // Kubernetes liveness/readiness 프로브는 인증 없이 접근 허용
    private static final String[] HEALTH_ENDPOINTS = { "/actuator/health", "/actuator/health/**" };

    private final InternalRequestTokenFilter internalRequestTokenFilter;

    /**
     * JWT 기반 Stateless 인증 필터 체인을 구성한다.
     */
    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                // CSRF는 브라우저 쿠키 기반 공격 방어용이므로 Bearer 헤더 기반 REST API에서는 불필요
                .csrf(AbstractHttpConfigurer::disable)
                // 세션 비활성화: JWT 토큰 기반 인증이므로 세션 필요 없음. (서버 측 상태 관리 불필요)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 인증 요청 권한 설정
                .authorizeHttpRequests(auth -> auth
                        // Kubernetes liveness/readiness 프로브는 인증 없이 접근 허용
                        .requestMatchers(HEALTH_ENDPOINTS).permitAll()
                        .requestMatchers(HttpMethod.POST, "/bids").authenticated()
                        .requestMatchers(HttpMethod.GET, "/bids/me").authenticated()
                        .anyRequest().permitAll())
                // Gateway 시크릿 검증을 JWT 검증(BearerTokenAuthenticationFilter)보다 먼저 수행
                // Gateway를 거치지 않은 직접 호출을 JWT 검증 전에 차단한다
                .addFilterBefore(internalRequestTokenFilter, BearerTokenAuthenticationFilter.class)
                // user-service JWKS 엔드포인트로 공개키를 가져와 JWT 서명·만료 검증
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
                .build();
    }
}
