package com.jaehoon.user.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaehoon.user.exception.ErrorResponse;
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

    private static final String MSG_UNAUTHORIZED = "인증이 필요합니다";

    private final JwtDecoder jwtDecoder;
    private final SecurityProperties securityProperties;
    private final ObjectMapper objectMapper;

    /**
     * JWT 기반 Stateless 인증 필터 체인을 구성한다.
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        String[] publicEndpoints = securityProperties.getPublicEndpoints().toArray(String[]::new);
        String refreshEndpoint = securityProperties.getRefreshEndpoint();
        DefaultBearerTokenResolver defaultResolver = new DefaultBearerTokenResolver();

        return http
                // CSRF 보안 비활성화: CSRF는 브라우저 쿠키 기반 공격을 방지하기 위한 보안 기능이므로, REST API 서버에서는 불필요.
                .csrf(AbstractHttpConfigurer::disable)
                // 세션 비활성화: JWT 토큰 기반 인증이므로 세션 필요 없음. (서버 측 상태 관리 불필요)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 인증 요청 권한 설정
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(publicEndpoints).permitAll()
                        .anyRequest().authenticated())
                // 미인증 요청(토큰 없음·만료·위조)에 대해 JSON 형식으로 401 반환
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, res, e) -> {
                            res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            res.setContentType("application/json;charset=UTF-8");
                            res.getWriter().write(objectMapper.writeValueAsString(new ErrorResponse(MSG_UNAUTHORIZED)));
                        }))
                // JWT 토큰 기반 인증 설정
                .oauth2ResourceServer(oauth2 -> oauth2
                        // refresh 엔드포인트는 Refresh Token을 Authorization 헤더로 수신하므로
                        // BearerTokenAuthenticationFilter의 Access Token 검증 대상에서 제외
                        // (웹 전용 서비스로 전환 시 HttpOnly 쿠키 방식이 XSS 방어에 유리 — user-service/CLAUDE.md 참고)
                        .bearerTokenResolver(request -> {
                            if (refreshEndpoint.equals(request.getServletPath())) return null;
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
