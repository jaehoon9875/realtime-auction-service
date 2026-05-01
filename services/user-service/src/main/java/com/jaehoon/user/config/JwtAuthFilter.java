package com.jaehoon.user.config;

import com.jaehoon.user.exception.InvalidTokenException;
import com.jaehoon.user.util.BearerTokenExtractor;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtProvider jwtProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String token = BearerTokenExtractor.extract(request.getHeader("Authorization"));

        // 토큰이 없으면 다음 필터로 넘김 (permitAll 경로는 그대로 통과)
        if (token == null) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            // parseToken을 한 번만 호출해 서명 검증 + Claims 추출을 동시에 처리
            Claims claims = jwtProvider.parseToken(token);
            String userId = claims.getSubject();

            // SecurityContext에 인증 정보 주입 (username = userId)
            UserDetails userDetails = User.withUsername(userId)
                    .password("")
                    .authorities(Collections.emptyList())
                    .build();

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (InvalidTokenException e) {
            // 유효하지 않은 토큰 → SecurityContext 비워두고 진행 (인증 필요 경로에서 403 반환됨)
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }
}
