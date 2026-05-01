package com.jaehoon.user.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

/**
 * JWT 설정값을 application.yml의 app.jwt 프리픽스로부터 바인딩.
 *
 * @Component 대신 @EnableConfigurationProperties(JwtProvider.class)로 빈 등록.
 * Spring Boot 3.x는 단일 생성자가 있으면 자동으로 생성자 바인딩을 사용하므로
 * @Setter 없이 final 필드로 불변 객체 구성이 가능함.
 */
@ConfigurationProperties(prefix = "app.jwt")
@Getter
public class JwtProvider {

    private final String secret;
    private final long accessTokenExpirationMs;
    private final long refreshTokenExpirationMs;

    public JwtProvider(String secret, long accessTokenExpirationMs, long refreshTokenExpirationMs) {
        this.secret = secret;
        this.accessTokenExpirationMs = accessTokenExpirationMs;
        this.refreshTokenExpirationMs = refreshTokenExpirationMs;
    }

    // 서명 키를 지연 초기화 (secret이 주입된 이후에 생성)
    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    // Access Token 생성 (subject: userId, claim: email)
    public String generateAccessToken(UUID userId, String email) {
        Date now = new Date();
        return Jwts.builder()
                .subject(userId.toString())
                .claim("email", email)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + accessTokenExpirationMs))
                .signWith(signingKey())
                .compact();
    }

    // Refresh Token은 랜덤 UUID 문자열 (Redis 저장 전용, 서명 불필요)
    public String generateRefreshToken() {
        return UUID.randomUUID().toString();
    }

    // 토큰 파싱 및 Claims 반환 (만료·위변조 검증 포함)
    public Claims parseToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(signingKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            throw new com.jaehoon.user.exception.InvalidTokenException("유효하지 않은 토큰입니다");
        }
    }

    // 토큰에서 userId(subject) 추출
    public UUID extractUserId(String token) {
        return UUID.fromString(parseToken(token).getSubject());
    }

    // 토큰에서 email claim 추출
    public String extractEmail(String token) {
        return parseToken(token).get("email", String.class);
    }
}
