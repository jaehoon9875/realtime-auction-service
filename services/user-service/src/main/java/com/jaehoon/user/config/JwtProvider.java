package com.jaehoon.user.config;

import com.jaehoon.user.exception.InvalidTokenException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

/**
 * JWT 설정값을 application.yml의 app.jwt 프리픽스로부터 바인딩.
 *
 * user-service는 RSA 개인키(privateKey)로 토큰을 서명하고,
 * API Gateway는 RSA 공개키(publicKey)로만 검증한다.
 * → Gateway가 서명 권한을 갖지 않으므로 토큰 위조 불가.
 *
 * @EnableConfigurationProperties(JwtProvider.class)로 빈 등록 (SecurityConfig 참고).
 */
@ConfigurationProperties(prefix = "app.jwt")
@Getter
public class JwtProvider {

    private final String privateKey;
    private final String publicKey;
    private final long accessTokenExpirationMs;
    private final long refreshTokenExpirationMs;

    public JwtProvider(String privateKey, String publicKey,
                       long accessTokenExpirationMs, long refreshTokenExpirationMs) {
        this.privateKey = privateKey;
        this.publicKey = publicKey;
        this.accessTokenExpirationMs = accessTokenExpirationMs;
        this.refreshTokenExpirationMs = refreshTokenExpirationMs;
    }

    // Access Token 생성 (subject: userId, claim: email) - RSA 개인키로 서명
    public String generateAccessToken(UUID userId, String email) {
        Date now = new Date();
        return Jwts.builder()
                .subject(userId.toString())
                .claim("email", email)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + accessTokenExpirationMs))
                .signWith(getPrivateKeyInstance(), Jwts.SIG.RS256)
                .compact();
    }

    // Refresh Token 생성 (subject: userId) - userId를 포함해 Redis 역방향 조회 없이 파싱 가능
    // jti(JWT ID)에 랜덤 UUID를 추가해 같은 userId·같은 초에 발급해도 항상 다른 토큰 보장
    public String generateRefreshToken(UUID userId) {
        Date now = new Date();
        return Jwts.builder()
                .subject(userId.toString())
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + refreshTokenExpirationMs))
                .signWith(getPrivateKeyInstance(), Jwts.SIG.RS256)
                .compact();
    }

    // 토큰 파싱 및 Claims 반환 (만료·위변조 검증 포함) - RSA 공개키로 검증
    public Claims parseToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(getPublicKeyInstance())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            throw new InvalidTokenException("유효하지 않은 토큰입니다");
        }
    }

    // 토큰에서 userId(subject) 추출
    public UUID extractUserId(String token) {
        try {
            return UUID.fromString(parseToken(token).getSubject());
        } catch (IllegalArgumentException e) {
            throw new InvalidTokenException("유효하지 않은 토큰입니다");
        }
    }

    // 토큰에서 email claim 추출
    public String extractEmail(String token) {
        return parseToken(token).get("email", String.class);
    }

    // Base64(PKCS8) 개인키 문자열 → java.security.PrivateKey (호출 시점 파싱)
    private PrivateKey getPrivateKeyInstance() {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(privateKey);
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
            return KeyFactory.getInstance("RSA").generatePrivate(spec);
        } catch (Exception e) {
            throw new IllegalStateException("JWT 개인키 로드 실패", e);
        }
    }

    // Base64(X509) 공개키 문자열 → java.security.PublicKey (호출 시점 파싱)
    private PublicKey getPublicKeyInstance() {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(publicKey);
            X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
            return KeyFactory.getInstance("RSA").generatePublic(spec);
        } catch (Exception e) {
            throw new IllegalStateException("JWT 공개키 로드 실패", e);
        }
    }
}
