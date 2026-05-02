package com.jaehoon.gateway.filter;

import com.jaehoon.gateway.config.JwtGatewayProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.List;

@Component
public class JwtAuthGlobalFilter implements GlobalFilter, Ordered {

    // 부팅 시 한 번만 파싱해 캐싱 — 매 요청마다 역직렬화하는 비용 제거
    private final PublicKey publicKey;

    public JwtAuthGlobalFilter(JwtGatewayProperties jwtProperties) {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(jwtProperties.getPublicKey());
            this.publicKey = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(keyBytes));
        } catch (Exception e) {
            throw new IllegalStateException("JWT 공개키 로드 실패", e);
        }
    }

    // 인증 없이 통과시킬 공개 경로 (정확 일치만 허용 - prefix 매칭 금지)
    // startsWith 대신 contains를 사용해 /actuator/env, /api/users/login-anything 같은
    // 의도치 않은 경로 노출을 차단한다.
    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/users/signup",
            "/api/users/login",
            "/api/users/refresh",
            "/actuator/health"
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

        // JWT 파싱·검증 (RSA 공개키 사용)
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

    // 공개 경로 여부 판단 (정확 일치)
    private boolean isPublicPath(String path) {
        return PUBLIC_PATHS.contains(path);
    }

    // Authorization: Bearer {token} 헤더에서 토큰 추출
    private String extractBearerToken(ServerWebExchange exchange) {
        String header = exchange.getRequest().getHeaders().getFirst("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }

    // RSA 공개키로 JWT 파싱·검증 (캐싱된 publicKey 사용)
    private Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(publicKey)
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
