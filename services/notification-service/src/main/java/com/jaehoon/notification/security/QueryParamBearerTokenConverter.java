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

    @Override
    public Mono<Authentication> convert(ServerWebExchange exchange) {
        String token = resolveFromAuthorizationHeader(exchange);
        if (!StringUtils.hasText(token)) {
            token = exchange.getRequest().getQueryParams().getFirst(TOKEN_QUERY_PARAM);
        }
        if (!StringUtils.hasText(token)) {
            return Mono.empty();
        }
        return Mono.just(new BearerTokenAuthenticationToken(token));
    }

    private String resolveFromAuthorizationHeader(ServerWebExchange exchange) {
        String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization != null && authorization.startsWith(BEARER_PREFIX)) {
            return authorization.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}
