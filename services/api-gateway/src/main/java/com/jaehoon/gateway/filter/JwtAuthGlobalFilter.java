package com.jaehoon.gateway.filter;

import com.jaehoon.gateway.config.JwtGatewayProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtAuthGlobalFilter implements GlobalFilter, Ordered {

    private final JwtGatewayProperties jwtProperties;

    // 인증 없이 통과시킬 공개 경로 목록 (StripPrefix 적용 전 외부 경로 기준)
    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/users/signup",
            "/api/users/login",
            "/api/users/refresh",
            "/actuator/health",
            "/actuator"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        // 공개 경로는 JWT 검증 없이 통과
        if (isPublicPath(path)) {
            return chain.filter(exchange);
        }

        // Authorization 헤더에서 Bearer 토큰 추출
        String token = extractBearerToken(exchange);
        if (token == null) {
            return unauthorized(exchange);
        }

        // JWT 파싱·검증
        Claims claims;
        try {
            claims = parseToken(token);
        } catch (JwtException | IllegalArgumentException e) {
            return unauthorized(exchange);
        }

        // 검증 성공 → X-User-Id, X-User-Email 헤더 추가 후 다음 필터로
        String userId = claims.getSubject();
        String email = claims.get("email", String.class);

        ServerHttpRequest mutatedRequest = exchange.getRequest()
                .mutate()
                .header("X-User-Id", userId)
                .header("X-User-Email", email != null ? email : "")
                .build();

        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    // 라우팅·StripPrefix 필터보다 먼저 실행되도록 높은 우선순위 부여
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }

    // 공개 경로 여부 판단 (prefix 매칭)
    private boolean isPublicPath(String path) {
        return PUBLIC_PATHS.stream().anyMatch(path::startsWith);
    }

    // Authorization: Bearer {token} 헤더에서 토큰 추출
    private String extractBearerToken(ServerWebExchange exchange) {
        String header = exchange.getRequest().getHeaders().getFirst("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }

    // JJWT 0.12.x API로 토큰 파싱·검증
    private Claims parseToken(String token) {
        SecretKey key = Keys.hmacShaKeyFor(
                jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8)
        );
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    // 401 응답 반환 후 체인 종료
    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }
}
