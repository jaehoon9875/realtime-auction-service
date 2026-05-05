package com.jaehoon.bid.config;

import java.io.IOException;
import java.util.Arrays;
import java.util.Set;

import org.springframework.core.env.Environment;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * Gateway가 추가한 내부 시크릿 헤더를 검증한다.
 */
@Component
@RequiredArgsConstructor
public class InternalRequestTokenFilter extends OncePerRequestFilter {

    private static final Set<String> NON_PROD_PROFILES = Set.of("local", "test", "integration");

    private final BidSecurityProperties securityProperties;
    private final Environment environment;

    @PostConstruct
    void validateSecret() {
        boolean isNonProd = Arrays.stream(environment.getActiveProfiles())
                .anyMatch(NON_PROD_PROFILES::contains);
        if (!isNonProd && !StringUtils.hasText(securityProperties.internalRequestSecret())) {
            throw new IllegalStateException(
                    "INTERNAL_REQUEST_SECRET이 설정되지 않았습니다. 운영 환경에서는 반드시 설정해야 합니다.");
        }
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {
        if (!StringUtils.hasText(securityProperties.internalRequestSecret())) {
            filterChain.doFilter(request, response);
            return;
        }

        if (isHealthEndpoint(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String expected = securityProperties.internalRequestSecret();
        String actual = request.getHeader(securityProperties.internalRequestHeaderName());
        if (expected.equals(actual)) {
            filterChain.doFilter(request, response);
            return;
        }

        response.sendError(HttpServletResponse.SC_FORBIDDEN, "내부 요청 토큰이 올바르지 않습니다.");
    }

    private boolean isHealthEndpoint(HttpServletRequest request) {
        String path = request.getRequestURI();
        return "/actuator/health".equals(path) || path.startsWith("/actuator/health/");
    }
}
