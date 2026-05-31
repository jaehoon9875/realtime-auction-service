package com.jaehoon.gateway.filter;

import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import reactor.core.publisher.Mono;

/**
 * 정상 HTTP 요청도 Loki와 Tempo에서 연결할 수 있도록 완료 로그를 남긴다.
 */
@Component
public class HttpRequestLoggingFilter implements WebFilter {

    private static final Logger log = LoggerFactory.getLogger(HttpRequestLoggingFilter.class);

    /**
     * Actuator 외 HTTP 요청이 끝나면 로그를 남긴다.
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if ("/actuator".equals(path) || path.startsWith("/actuator/")) {
            return chain.filter(exchange);
        }

        long startedAt = System.nanoTime();
        return chain.filter(exchange)
                .doFinally(signalType -> logRequest(exchange, startedAt));
    }

    private void logRequest(ServerWebExchange exchange, long startedAt) {
        HttpStatusCode statusCode = exchange.getResponse().getStatusCode();
        int status = statusCode != null ? statusCode.value() : 200;
        long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);

        // traceId/spanId는 Spring Boot 4.x logstash 포맷이 Micrometer MDC에서 자동 포함하므로 중복 추가하지 않는다
        log.atInfo()
                .addKeyValue("httpMethod", exchange.getRequest().getMethod().name())
                .addKeyValue("path", exchange.getRequest().getPath().value())
                .addKeyValue("status", status)
                .addKeyValue("durationMs", durationMs)
                .log("HTTP 요청 처리 완료");
    }
}
