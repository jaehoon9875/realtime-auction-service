package com.jaehoon.user.config;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.jaehoon.user.exception.InvalidTokenException;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import lombok.Getter;

/**
 * JWT 발급·검증에 필요한 RSA 키 쌍과 만료 설정을 보관하는 설정 빈.
 *
 * <p>
 * application.yml의 {@code app.jwt} 프리픽스로부터 바인딩된다.
 * RSA 개인키로 토큰을 서명하고, API Gateway는 공개키로만 검증한다.
 * Gateway가 서명 권한을 갖지 않으므로 토큰 위조가 불가능하다.
 * </p>
 *
 * <p>
 * {@link JwtConfig}의 {@code @EnableConfigurationProperties}로 빈 등록한다.
 * </p>
 */
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProvider {

    private final String privateKey;
    private final String publicKey;
    private final long accessTokenExpirationMs;

    @Getter
    private final long refreshTokenExpirationMs;

    // 생성자에서 1회만 파싱해 캐싱 — JWKS·JwtDecoder·토큰 검증에서 재사용
    private final RSAPublicKey rsaPublicKey;

    // 공개키와 동일하게 생성자에서 캐싱 — 토큰 서명 시마다 재파싱하지 않기 위해
    private final PrivateKey rsaPrivateKey;

    public JwtProvider(String privateKey, String publicKey,
            long accessTokenExpirationMs, long refreshTokenExpirationMs) {
        this.privateKey = privateKey;
        this.publicKey = publicKey;
        this.accessTokenExpirationMs = accessTokenExpirationMs;
        this.refreshTokenExpirationMs = refreshTokenExpirationMs;
        this.rsaPublicKey = parsePublicKey(publicKey);
        this.rsaPrivateKey = parsePrivateKey(privateKey);
    }

    /**
     * Access Token을 생성한다.
     *
     * <p>
     * subject에 userId, claim에 email을 포함하며 RSA 개인키로 서명한다.
     * </p>
     *
     * @param userId 토큰 subject로 설정할 사용자 ID
     * @param email  토큰 claim에 포함할 이메일
     * @return 서명된 JWT 문자열
     */
    public String generateAccessToken(UUID userId, String email) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId.toString())
                .claim("email", email)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(accessTokenExpirationMs)))
                .signWith(rsaPrivateKey, Jwts.SIG.RS256)
                .compact();
    }

    /**
     * Refresh Token을 생성한다.
     *
     * <p>
     * subject에 userId를 포함하여 Redis 역방향 조회 없이 파싱할 수 있다.
     * jti에 랜덤 UUID를 부여해 동일 userId·동일 시각 발급 시에도 토큰이 항상 다름을 보장한다.
     * </p>
     *
     * @param userId 토큰 subject로 설정할 사용자 ID
     * @return 서명된 JWT 문자열
     */
    public String generateRefreshToken(UUID userId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId.toString())
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(refreshTokenExpirationMs)))
                .signWith(rsaPrivateKey, Jwts.SIG.RS256)
                .compact();
    }

    /**
     * 토큰을 파싱하고 Claims를 반환한다. 만료 및 위변조 검증을 포함한다.
     *
     * @param token 검증할 JWT 문자열
     * @return 파싱된 Claims
     * @throws InvalidTokenException 토큰이 만료되었거나 서명이 유효하지 않은 경우
     */
    public Claims parseToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(rsaPublicKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            throw new InvalidTokenException("유효하지 않은 토큰입니다");
        }
    }

    /**
     * 토큰에서 userId(subject)를 추출한다.
     *
     * @param token JWT 문자열
     * @return 토큰 subject에 저장된 사용자 ID
     * @throws InvalidTokenException 토큰이 유효하지 않거나 subject가 UUID 형식이 아닌 경우
     */
    public UUID extractUserId(String token) {
        try {
            return UUID.fromString(parseToken(token).getSubject());
        } catch (IllegalArgumentException e) {
            throw new InvalidTokenException("유효하지 않은 토큰입니다");
        }
    }

    /**
     * 토큰에서 email claim을 추출한다.
     *
     * @param token JWT 문자열
     * @return 토큰에 포함된 이메일
     */
    public String extractEmail(String token) {
        return parseToken(token).get("email", String.class);
    }

    /**
     * JWKS 엔드포인트 및 JwtDecoder 생성에 사용할 RSA 공개키를 반환한다.
     *
     * @return 생성 시 캐싱된 RSAPublicKey 인스턴스
     */
    public RSAPublicKey getRSAPublicKey() {
        return rsaPublicKey;
    }

    // Base64(X.509) 공개키 문자열 → RSAPublicKey. 생성자에서 1회만 파싱해 캐싱한다.
    private static RSAPublicKey parsePublicKey(String publicKey) {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(publicKey);
            X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
            return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(spec);
        } catch (Exception e) {
            throw new IllegalStateException("JWT 공개키 로드 실패", e);
        }
    }

    // Base64(PKCS8) 개인키 문자열 → PrivateKey. 공개키와 마찬가지로 생성자에서 1회만 파싱해 캐싱한다.
    private static PrivateKey parsePrivateKey(String privateKey) {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(privateKey);
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
            return KeyFactory.getInstance("RSA").generatePrivate(spec);
        } catch (Exception e) {
            throw new IllegalStateException("JWT 개인키 로드 실패", e);
        }
    }
}
