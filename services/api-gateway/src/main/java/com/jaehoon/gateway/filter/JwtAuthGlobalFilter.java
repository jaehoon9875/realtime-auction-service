package com.jaehoon.gateway.filter;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Set;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;

import com.jaehoon.gateway.config.GatewaySecurityProperties;
import com.jaehoon.gateway.config.JwtGatewayProperties;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import reactor.core.publisher.Mono;

/**
 * 모든 라우트에 적용되는 JWT 검증 필터.
 * 토큰이 있으면 서명·만료를 검사하고, 실패 시 401로 차단한다.
 * 로그인·회원가입 등은 토큰 유효성과 무관하게 통과한다.
 */
@Component
public class JwtAuthGlobalFilter implements GlobalFilter, Ordered {

    /**
     * JWT 인증 필터 실행 순서.
     * - Spring Cloud Gateway 내장 필터(RouteToRequestUrlFilter: 10000 등)보다 먼저 실행.
     * - HIGHEST_PRECEDENCE(Integer.MIN_VALUE) 대역 내장 필터와의 충돌 방지를 위해 음수 소값 사용.
     */
    private static final int ORDER = -100;

    // JWT 공개키. 부팅 시 한 번만 파싱해 캐싱 — 매 요청마다 역직렬화하는 비용 제거.
    private final PublicKey publicKey;

    // 공개 엔드포인트 목록. contains() 호출이 모든 요청마다 실행되므로 O(1) 탐색을 위해 Set으로 보관.
    private final Set<String> publicEndpoints;

    /**
     * JwtAuthGlobalFilter 생성자
     * 
     * @param jwtProperties      JWT 공개키 속성
     * @param securityProperties JWT 검증을 건너뛰는 공개 엔드포인트 목록
     */
    public JwtAuthGlobalFilter(JwtGatewayProperties jwtProperties,
            GatewaySecurityProperties securityProperties) {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(jwtProperties.getPublicKey());
            this.publicKey = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(keyBytes));
        } catch (Exception e) {
            throw new IllegalStateException("JWT 공개키 로드 실패", e);
        }
        this.publicEndpoints = Set.copyOf(securityProperties.getPublicEndpoints());
    }

    /**
     * 라우팅·StripPrefix 필터보다 먼저 실행되도록 높은 우선순위 부여
     */
    @Override
    public int getOrder() {
        return ORDER;
    }

    /**
     * JWT 인증 필터 실행
     * 
     * @param exchange 서버 웹 교환. HTTP 요청 정보를 포함한다.
     * @param chain    게이트웨이 필터 체인. 다음 필터를 호출하는 데 사용된다.
     * @return Mono<Void> 체인 필터.
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {

        // 로그인·회원가입 등 토큰이 사용되지 않는 엔드포인트는 통과.
        String path = exchange.getRequest().getURI().getPath();
        if (publicEndpoints.contains(path)) {
            return chain.filter(exchange);
        }

        String token = extractBearerToken(exchange);

        // 공개 엔드포인트가 아니면서 토큰이 필요 없는 경우는 통과. (ex 경매 목록 조회)
        // 인증 필요 여부는 각 서비스 SecurityConfig(permitAll/authenticated)가 결정한다.
        if (token == null) {
            return chain.filter(exchange);
        }

        // JWT 있으면 검증 — 위조·만료 토큰은 차단
        try {
            parseToken(token);
        } catch (JwtException | IllegalArgumentException e) {
            return unauthorized(exchange);
        }

        // 검증 성공 → Authorization 헤더 그대로 전달, 각 서비스가 JWT 직접 검증
        return chain.filter(exchange);
    }

    // Authorization: Bearer {token} 헤더에서 토큰 추출
    private String extractBearerToken(ServerWebExchange exchange) {
        String header = exchange.getRequest().getHeaders().getFirst("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }

    // RSA 공개키로 JWT 서명·만료 검증 (캐싱된 publicKey 사용)
    private void parseToken(String token) {
        Jwts.parser()
                .verifyWith(publicKey)
                .build()
                .parseSignedClaims(token);
    }

    // 401 응답 반환 후 체인 종료
    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }
}
