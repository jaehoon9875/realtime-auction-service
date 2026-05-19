package com.jaehoon.user.config;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security 필터 체인 및 인증 정책을 구성한다.
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(SecurityProperties.class)
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtDecoder jwtDecoder;
    private final SecurityProperties securityProperties;

    /**
     * JWT 기반 Stateless 인증 필터 체인을 구성한다.
     *
     * @throws Exception Spring Security 설정 중 발생하는 예외
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        String[] publicEndpoints = securityProperties.getPublicEndpoints().toArray(String[]::new);
        DefaultBearerTokenResolver defaultResolver = new DefaultBearerTokenResolver();

        return http
                // REST API 서버 → CSRF 불필요
                .csrf(AbstractHttpConfigurer::disable)
                // JWT 사용 → 세션 미사용
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // 공개 엔드포인트는 app.security.public-endpoints 에서 관리
                        .requestMatchers(publicEndpoints).permitAll()
                        .anyRequest().authenticated()
                )
                // 미인증 요청(토큰 없음·만료·위조)에 대해 JSON 형식으로 401 반환
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, res, e) -> {
                            res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            res.setContentType("application/json;charset=UTF-8");
                            res.getWriter().write("{\"message\":\"인증이 필요합니다\"}");
                        }))
                .oauth2ResourceServer(oauth2 -> oauth2
                        // /users/refresh는 Refresh Token을 Authorization 헤더로 수신하므로
                        // BearerTokenAuthenticationFilter의 Access Token 검증 대상에서 제외
                        .bearerTokenResolver(request -> {
                            if ("/users/refresh".equals(request.getRequestURI())) return null;
                            return defaultResolver.resolve(request);
                        })
                        .jwt(jwt -> jwt.decoder(jwtDecoder)))
                .build();
    }

    /**
     * BCrypt 알고리즘으로 비밀번호를 해싱하는 인코더를 등록한다.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
