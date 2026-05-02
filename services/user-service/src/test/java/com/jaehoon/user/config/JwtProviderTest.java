package com.jaehoon.user.config;

import com.jaehoon.user.exception.InvalidTokenException;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class JwtProviderTest {

    private static final long ACCESS_MS  = 900_000L;      // 15분
    private static final long REFRESH_MS = 604_800_000L;  // 7일

    private static KeyPair keyPair;
    private JwtProvider jwtProvider;

    @BeforeAll
    static void generateKeyPair() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        keyPair = gen.generateKeyPair();
    }

    @BeforeEach
    void setUp() {
        String encodedPrivate = Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded());
        String encodedPublic  = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
        jwtProvider = new JwtProvider(encodedPrivate, encodedPublic, ACCESS_MS, REFRESH_MS);
    }

    // ───────────────────────────────────────────────
    // generateAccessToken
    // ───────────────────────────────────────────────

    @Test
    @DisplayName("Access Token 생성 - subject에 userId, email claim 포함")
    void generateAccessToken_정상() {
        UUID userId = UUID.randomUUID();
        String email = "test@example.com";

        String token = jwtProvider.generateAccessToken(userId, email);

        assertThat(token).isNotBlank();
        Claims claims = jwtProvider.parseToken(token);
        assertThat(claims.getSubject()).isEqualTo(userId.toString());
        assertThat(claims.get("email", String.class)).isEqualTo(email);
    }

    @Test
    @DisplayName("Access Token 생성 - 만료시각이 현재보다 미래")
    void generateAccessToken_만료시각_미래() {
        String token = jwtProvider.generateAccessToken(UUID.randomUUID(), "test@example.com");

        Claims claims = jwtProvider.parseToken(token);
        assertThat(claims.getExpiration()).isAfter(claims.getIssuedAt());
    }

    // ───────────────────────────────────────────────
    // generateRefreshToken
    // ───────────────────────────────────────────────

    @Test
    @DisplayName("Refresh Token 생성 - subject에 userId 포함된 서명된 JWT")
    void generateRefreshToken_userId포함() {
        UUID userId = UUID.randomUUID();

        String refreshToken = jwtProvider.generateRefreshToken(userId);

        assertThat(refreshToken).isNotBlank();
        // Refresh Token도 파싱 가능한 JWT여야 함
        Claims claims = jwtProvider.parseToken(refreshToken);
        assertThat(claims.getSubject()).isEqualTo(userId.toString());
    }

    @Test
    @DisplayName("Refresh Token은 호출할 때마다 다른 값 생성")
    void generateRefreshToken_호출마다다름() {
        UUID userId = UUID.randomUUID();

        String token1 = jwtProvider.generateRefreshToken(userId);
        String token2 = jwtProvider.generateRefreshToken(userId);

        assertThat(token1).isNotEqualTo(token2);
    }

    // ───────────────────────────────────────────────
    // parseToken
    // ───────────────────────────────────────────────

    @Test
    @DisplayName("만료된 토큰 파싱 시 InvalidTokenException")
    void parseToken_만료토큰_예외() {
        // exp를 issuedAt보다 과거로 두어 iat=exp(0ms) 경계에서의 플래키 방지
        JwtProvider shortLived = new JwtProvider(
                Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded()),
                Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded()),
                -1000L, REFRESH_MS);
        String expiredToken = shortLived.generateAccessToken(UUID.randomUUID(), "test@example.com");

        assertThatThrownBy(() -> jwtProvider.parseToken(expiredToken))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    @DisplayName("서명이 변조된 토큰 파싱 시 InvalidTokenException")
    void parseToken_변조토큰_예외() {
        String token = jwtProvider.generateAccessToken(UUID.randomUUID(), "test@example.com");
        String tampered = token + "tampered";

        assertThatThrownBy(() -> jwtProvider.parseToken(tampered))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    @DisplayName("다른 키 쌍으로 서명된 토큰 파싱 시 InvalidTokenException")
    void parseToken_다른키쌍_예외() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair otherKeyPair = gen.generateKeyPair();

        JwtProvider other = new JwtProvider(
                Base64.getEncoder().encodeToString(otherKeyPair.getPrivate().getEncoded()),
                Base64.getEncoder().encodeToString(otherKeyPair.getPublic().getEncoded()),
                ACCESS_MS, REFRESH_MS);
        String token = other.generateAccessToken(UUID.randomUUID(), "test@example.com");

        assertThatThrownBy(() -> jwtProvider.parseToken(token))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    @DisplayName("빈 문자열 토큰 파싱 시 InvalidTokenException")
    void parseToken_빈문자열_예외() {
        assertThatThrownBy(() -> jwtProvider.parseToken(""))
                .isInstanceOf(InvalidTokenException.class);
    }

    // ───────────────────────────────────────────────
    // extractUserId / extractEmail
    // ───────────────────────────────────────────────

    @Test
    @DisplayName("extractUserId - Access Token subject에서 userId 추출")
    void extractUserId_정상() {
        UUID userId = UUID.randomUUID();
        String token = jwtProvider.generateAccessToken(userId, "test@example.com");

        UUID extracted = jwtProvider.extractUserId(token);

        assertThat(extracted).isEqualTo(userId);
    }

    @Test
    @DisplayName("extractEmail - Access Token email claim 추출")
    void extractEmail_정상() {
        String email = "test@example.com";
        String token = jwtProvider.generateAccessToken(UUID.randomUUID(), email);

        String extracted = jwtProvider.extractEmail(token);

        assertThat(extracted).isEqualTo(email);
    }
}
