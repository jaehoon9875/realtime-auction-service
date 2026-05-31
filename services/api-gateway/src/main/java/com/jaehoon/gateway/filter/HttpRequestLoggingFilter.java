package com.jaehoon.gateway.filter;

import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import io.micrometer.observation.Observation;
import io.micrometer.observation.contextpropagation.ObservationThreadLocalAccessor;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import reactor.core.publisher.Mono;

/**
 * 정상 HTTP 요청도 Loki와 Tempo에서 연결할 수 있도록 완료 로그를 남긴다.
 */
@Component
public class HttpRequestLoggingFilter implements WebFilter {

    private static final Logger log = LoggerFactory.getLogger(HttpRequestLoggingFilter.class);

    private final ObjectProvider<Tracer> tracerProvider;

    /**
     * HTTP 요청 완료 로그 필터를 생성한다.
     *
     * @param tracerProvider 현재 요청의 트레이스 식별자 조회용 tracer provider
     */
    public HttpRequestLoggingFilter(ObjectProvider<Tracer> tracerProvider) {
        this.tracerProvider = tracerProvider;
    }

    /**
     * Actuator 외 HTTP 요청이 끝나면 트레이스 식별자를 포함한 로그를 남긴다.
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (exchange.getRequest().getPath().value().startsWith("/actuator/")) {
            return chain.filter(exchange);
        }

        return Mono.deferContextual(contextView -> {
            long startedAt = System.nanoTime();
            Tracer tracer = tracerProvider.getIfAvailable();
            Observation observation = contextView.getOrDefault(ObservationThreadLocalAccessor.KEY, null);
            Span span = currentSpan(tracer, observation);
            String traceId = span != null ? span.context().traceId() : null;
            String spanId = span != null ? span.context().spanId() : null;

            return chain.filter(exchange)
                    .doFinally(signalType -> logRequest(exchange, traceId, spanId, startedAt));
        });
    }

    private Span currentSpan(Tracer tracer, Observation observation) {
        if (tracer == null) {
            return null;
        }
        if (observation == null) {
            return tracer.currentSpan();
        }
        try (Observation.Scope ignored = observation.openScope()) {
            return tracer.currentSpan();
        }
    }

    private void logRequest(ServerWebExchange exchange, String traceId, String spanId, long startedAt) {
        HttpStatusCode statusCode = exchange.getResponse().getStatusCode();
        int status = statusCode != null ? statusCode.value() : 200;
        long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);

        log.atInfo()
                .addKeyValue("traceId", traceId)
                .addKeyValue("spanId", spanId)
                .addKeyValue("httpMethod", exchange.getRequest().getMethod().name())
                .addKeyValue("path", exchange.getRequest().getPath().value())
                .addKeyValue("status", status)
                .addKeyValue("durationMs", durationMs)
                .log("HTTP 요청 처리 완료");
    }
}
