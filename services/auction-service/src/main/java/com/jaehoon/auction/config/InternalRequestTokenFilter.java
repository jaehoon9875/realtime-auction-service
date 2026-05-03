package com.jaehoon.auction.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Gateway가 추가한 내부 시크릿 헤더가 설정값과 일치하는지 검사한다.
 * 시크릿이 비어 있으면 검증을 건너뛴다(로컬에서 Gateway 없이 기동할 때).
 */
@Component
@RequiredArgsConstructor
public class InternalRequestTokenFilter extends OncePerRequestFilter {

    private final AuctionSecurityProperties securityProperties;

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
