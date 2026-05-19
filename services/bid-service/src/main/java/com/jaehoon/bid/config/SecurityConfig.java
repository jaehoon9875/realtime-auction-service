package com.jaehoon.bid.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import lombok.RequiredArgsConstructor;

@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(BidSecurityProperties.class)
@RequiredArgsConstructor
public class SecurityConfig {

    private final InternalRequestTokenFilter internalRequestTokenFilter;

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/bids").authenticated()
                        .requestMatchers(HttpMethod.GET, "/bids/me").authenticated()
                        .anyRequest().permitAll())
                // InternalRequestTokenFilter: 내부 서비스 호출 검증 (oauth2ResourceServer보다 먼저 실행)
                .addFilterBefore(internalRequestTokenFilter, UsernamePasswordAuthenticationFilter.class)
                // JWKS 엔드포인트(user-service)로 공개키를 가져와 JWT 직접 검증
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
                .build();
    }
}
