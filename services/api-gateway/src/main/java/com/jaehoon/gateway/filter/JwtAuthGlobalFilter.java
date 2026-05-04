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

    // 만료·위조 토큰이 있어도 통과시켜야 하는 경로 (로그인·갱신 등)
    // JWT가 없는 요청은 이 목록과 무관하게 모두 통과한다 — 인가는 각 서비스가 결정
    private static final List<String> SKIP_JWT_PATHS = List.of(
            "/api/users/signup",
            "/api/users/login",
            "/api/users/refresh",
            "/actuator/health"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 클라이언트가 직접 심은 인증 헤더를 모든 요청에서 먼저 제거
        // — SKIP 경로 포함, JWT 없는 경우 포함. 검증 성공 후에만 다시 주입한다.
        exchange = exchange.mutate()
                .request(r -> r.headers(h -> {
                    h.remove("X-User-Id");
                    h.remove("X-User-Email");
                }))
                .build();

        String path = exchange.getRequest().getURI().getPath();

        // 로그인·회원가입 등은 토큰 유효성과 무관하게 통과
        if (SKIP_JWT_PATHS.contains(path)) {
            return chain.filter(exchange);
        }

        String token = extractBearerToken(exchange);

        // JWT 없으면 인증 정보 없이 통과 — 인가는 각 서비스가 판단
        if (token == null) {
            return chain.filter(exchange);
        }

        // JWT 있으면 검증 — 위조·만료 토큰은 차단
        Claims claims;
        try {
            claims = parseToken(token);
        } catch (JwtException | IllegalArgumentException e) {
            return unauthorized(exchange);
        }

        // 검증 성공 → X-User-Id, X-User-Email 헤더 주입 후 다음 필터로
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
