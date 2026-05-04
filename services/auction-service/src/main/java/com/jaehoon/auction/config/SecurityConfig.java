package com.jaehoon.auction.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(AuctionSecurityProperties.class)
@RequiredArgsConstructor
public class SecurityConfig {

    private final InternalRequestTokenFilter internalRequestTokenFilter;
    private final GatewayUserFilter gatewayUserFilter;

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                // 브라우저 폼·세션 CSRF가 아닌 Bearer/헤더 기반 API이므로 비활성화
                .csrf(AbstractHttpConfigurer::disable)
                // 서버 세션을 만들지 않음(REST)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // 경매 목록·상세 조회는 비로그인 허용
                        .requestMatchers(HttpMethod.GET, "/auctions", "/auctions/*").permitAll()
                        // Kubernetes liveness/readiness(/actuator/health/**) 프로브도 인증 없이 접근 허용
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        // 경매 생성·상태 변경 등 나머지는 인증 필요
                        .anyRequest().authenticated()
                )
                // 실행 순서: InternalRequestTokenFilter → GatewayUserFilter → UsernamePasswordAuthenticationFilter
                .addFilterBefore(internalRequestTokenFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(gatewayUserFilter, InternalRequestTokenFilter.class)
                .build();
    }
}
