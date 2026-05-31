package com.jaehoon.user.config;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 정상 HTTP 요청도 Loki와 Tempo에서 연결할 수 있도록 완료 로그를 남긴다.
 */
@Component
public class HttpRequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(HttpRequestLoggingFilter.class);

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return "/actuator".equals(path) || path.startsWith("/actuator/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        long startedAt = System.nanoTime();

        try {
            filterChain.doFilter(request, response);
        } finally {
            // traceId/spanId는 Spring Boot 4.x logstash 포맷이 Micrometer MDC에서 자동 포함하므로 중복 추가하지 않는다
            log.atInfo()
                    .addKeyValue("httpMethod", request.getMethod())
                    .addKeyValue("path", request.getRequestURI())
                    .addKeyValue("status", response.getStatus())
                    .addKeyValue("durationMs", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt))
                    .log("HTTP 요청 처리 완료");
        }
    }
}
