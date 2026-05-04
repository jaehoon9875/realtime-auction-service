package com.jaehoon.user.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

/**
 * Gateway가 JWT 검증 후 주입한 X-User-Id 헤더를 읽어 SecurityContext를 설정한다.
 * JWT 재파싱 없음 — 인증 검증 책임은 Gateway에만 있다.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    /** 상태 없는 유틸이므로 필드로 한 번만 생성해 재사용한다 */
    private final WebAuthenticationDetailsSource authenticationDetailsSource = new WebAuthenticationDetailsSource();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String userId = request.getHeader("X-User-Id");

        // X-User-Id 없으면 인증 없이 통과 (공개 경로는 Security permitAll로 처리)
        if (StringUtils.hasText(userId)) {
            UserDetails userDetails = User.withUsername(userId)
                    .password("")
                    .authorities(Collections.emptyList())
                    .build();

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
            authentication.setDetails(authenticationDetailsSource.buildDetails(request));

            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        filterChain.doFilter(request, response);
    }
}
