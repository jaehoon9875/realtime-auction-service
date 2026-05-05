package com.jaehoon.bid.config;

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

import lombok.RequiredArgsConstructor;

@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(BidSecurityProperties.class)
@RequiredArgsConstructor
public class SecurityConfig {

    private final InternalRequestTokenFilter internalRequestTokenFilter;
    private final GatewayUserFilter gatewayUserFilter;

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
                .addFilterBefore(internalRequestTokenFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(gatewayUserFilter, InternalRequestTokenFilter.class)
                .build();
    }
}
