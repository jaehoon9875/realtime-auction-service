package com.jaehoon.auction.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                // 브라우저 폼·세션 CSRF가 아닌 Bearer/헤더 기반 API이므로 비활성화
                .csrf(AbstractHttpConfigurer::disable)
                // 서버 세션을 만들지 않음(REST)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // JWT 재검증은 하지 않고, InternalRequestTokenFilter에서 내부 시크릿만 검사
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                // UsernamePasswordAuthenticationFilter 앞에 두어 모든 요청에서 시크릿 검사
                .addFilterBefore(internalRequestTokenFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
