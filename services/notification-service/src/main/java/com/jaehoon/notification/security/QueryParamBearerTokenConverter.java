package com.jaehoon.notification.security;

import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * JWT Bearer 토큰을 HTTP 요청에서 추출한다.
 * Authorization 헤더를 우선하고, 없으면 WebSocket 업그레이드용 ?token= 쿼리 파라미터를 사용한다.
 */
public class QueryParamBearerTokenConverter implements ServerAuthenticationConverter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String TOKEN_QUERY_PARAM = "token";

    /**
     * 요청에서 Bearer JWT를 추출해 Authentication으로 변환한다.
     * Authorization 헤더를 우선하고, 없으면 {@code token} 쿼리 파라미터를 사용한다.
     * 
     * @param exchange 현재 WebFlux 요청/응답
     * @return 토큰이 있으면 BearerTokenAuthenticationToken, 없으면 빈 Mono
     */
    @Override
    public Mono<Authentication> convert(ServerWebExchange exchange) {
        String token = resolveFromAuthorizationHeader(exchange);
        if (!StringUtils.hasText(token) && isWebSocketHandshake(exchange)) {
            token = exchange.getRequest().getQueryParams().getFirst(TOKEN_QUERY_PARAM);
        }
        if (!StringUtils.hasText(token)) {
            return Mono.empty();
        }
        return Mono.just(new BearerTokenAuthenticationToken(token));
    }

    // Authorization 헤더에서 Bearer 토큰을 추출한다.
    private String resolveFromAuthorizationHeader(ServerWebExchange exchange) {
        String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization != null && authorization.startsWith(BEARER_PREFIX)) {
            return authorization.substring(BEARER_PREFIX.length());
        }
        return null;
    }

    // ?token= 폴백은 브라우저 WebSocket API가 커스텀 헤더를 지원하지 않아 불가피한 WebSocket 핸드셰이크에서만 허용한다.
    private boolean isWebSocketHandshake(ServerWebExchange exchange) {
        String upgrade = exchange.getRequest().getHeaders().getFirst(HttpHeaders.UPGRADE);
        String path = exchange.getRequest().getPath().value();
        return "websocket".equalsIgnoreCase(upgrade) && path.startsWith("/ws/");
    }
}
