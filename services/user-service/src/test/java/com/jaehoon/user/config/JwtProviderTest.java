package com.jaehoon.user.config;

import com.jaehoon.user.exception.InvalidTokenException;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class JwtProviderTest {

    // HMAC-SHA256 최소 요구: 256비트(32바이트) 이상
    private static final String SECRET = "test-secret-key-must-be-at-least-256-bits-long-for-hs256!!";
    private static final long ACCESS_MS  = 900_000L;      // 15분
    private static final long REFRESH_MS = 604_800_000L;  // 7일

    private JwtProvider jwtProvider;

    @BeforeEach
    void setUp() {
        jwtProvider = new JwtProvider(SECRET, ACCESS_MS, REFRESH_MS);
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
    @DisplayName("Refresh Token 생성 - UUID 형식 문자열")
    void generateRefreshToken_UUID형식() {
        String refreshToken = jwtProvider.generateRefreshToken();

        assertThat(refreshToken).isNotBlank();
        // UUID.fromString()이 예외 없이 파싱되면 유효한 UUID 형식
        assertThatCode(() -> UUID.fromString(refreshToken)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Refresh Token은 호출할 때마다 다른 값 생성")
    void generateRefreshToken_호출마다다름() {
        String token1 = jwtProvider.generateRefreshToken();
        String token2 = jwtProvider.generateRefreshToken();

        assertThat(token1).isNotEqualTo(token2);
    }

    // ───────────────────────────────────────────────
    // parseToken
    // ───────────────────────────────────────────────

    @Test
    @DisplayName("만료된 토큰 파싱 시 InvalidTokenException")
    void parseToken_만료토큰_예외() {
        // 만료 시간을 0ms로 설정 → 생성 즉시 만료
        JwtProvider shortLived = new JwtProvider(SECRET, 0L, REFRESH_MS);
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
    @DisplayName("다른 시크릿으로 서명된 토큰 파싱 시 InvalidTokenException")
    void parseToken_다른시크릿_예외() {
        JwtProvider other = new JwtProvider(
                "completely-different-secret-key-for-testing-purposes-!!", ACCESS_MS, REFRESH_MS);
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
