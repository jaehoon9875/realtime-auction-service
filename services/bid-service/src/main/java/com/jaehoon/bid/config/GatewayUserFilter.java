package com.jaehoon.bid.config;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Gateway가 전달한 X-User-Id를 SecurityContext에 등록한다.
 */
@Component
public class GatewayUserFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String userId = request.getHeader("X-User-Id");
        if (StringUtils.hasText(userId)) {
            // UUID 형식이 아니면 SecurityContext에 등록하지 않고 익명 사용자로 통과
            // — SecurityConfig 인가 규칙이 401/403으로 처리
            try {
                UUID.fromString(userId);
            } catch (IllegalArgumentException e) {
                filterChain.doFilter(request, response);
                return;
            }
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userId, null, List.of());
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
        filterChain.doFilter(request, response);
    }
}
