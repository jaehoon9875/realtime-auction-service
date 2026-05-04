package com.jaehoon.auction.config;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Gateway가 추가한 내부 시크릿 헤더가 설정값과 일치하는지 검사한다.
 * 개발·테스트 프로필: 시크릿 미설정 시 검증 생략 (Gateway 없이 단독 기동 허용)
 * 그 외 환경(운영 등): 시크릿 미설정 시 기동 거부 (fail-fast)
 */
@Component
@RequiredArgsConstructor
public class InternalRequestTokenFilter extends OncePerRequestFilter {

    // 시크릿 미설정을 허용하는 프로필 목록 (로컬 개발 및 테스트 환경)
    private static final Set<String> NON_PROD_PROFILES = Set.of("local", "test", "integration");

    private final AuctionSecurityProperties securityProperties;
    private final Environment environment;

    /**
     * 운영 환경(non-prod 프로필 아님)에서 시크릿이 비어 있으면 기동 즉시 실패.
     * INTERNAL_REQUEST_SECRET 미설정으로 인한 인증 우회를 원천 차단한다.
     */
    @PostConstruct
    void validateSecret() {
        boolean isNonProd = Arrays.stream(environment.getActiveProfiles())
                .anyMatch(NON_PROD_PROFILES::contains);
        if (!isNonProd && !StringUtils.hasText(securityProperties.internalRequestSecret())) {
            throw new IllegalStateException(
                    "INTERNAL_REQUEST_SECRET이 설정되지 않았습니다. " +
                    "운영 환경에서는 반드시 설정해야 합니다."
            );
        }
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        // 로컬 단독 실행: 시크릿 미설정 시 필터 통과
        if (!StringUtils.hasText(securityProperties.internalRequestSecret())) {
            filterChain.doFilter(request, response);
            return;
        }

        // 헬스체크는 프로브가 헤더를 붙이기 어려우므로 예외
        if (isHealthEndpoint(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String expected = securityProperties.internalRequestSecret();
        String headerName = securityProperties.internalRequestHeaderName();
        String actual = request.getHeader(headerName);

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
