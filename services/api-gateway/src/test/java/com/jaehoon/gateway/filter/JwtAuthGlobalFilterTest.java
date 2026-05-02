package com.jaehoon.gateway.filter;

import com.jaehoon.gateway.config.JwtGatewayProperties;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spring 컨텍스트 없이 MockServerWebExchange로 필터 로직을 직접 검증.
 * - 공개 경로: 체인 통과
 * - 비공개 경로 + JWT 없음/만료/변조: 401 반환
 * - 비공개 경로 + 유효 JWT: X-User-Id, X-User-Email 헤더 추가 후 체인 통과
 */
class JwtAuthGlobalFilterTest {

    private static KeyPair keyPair;

    private JwtGatewayProperties properties;
    private JwtAuthGlobalFilter filter;

    @BeforeAll
    static void generateKeyPair() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        keyPair = gen.generateKeyPair();
    }

    @BeforeEach
    void setUp() {
        properties = new JwtGatewayProperties();
        properties.setPublicKey(Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded()));
        filter = new JwtAuthGlobalFilter(properties);
    }

    // ─────────────────────────────────────────────────
    // 공개 경로 (permitAll)
    // ─────────────────────────────────────────────────

    @Test
    @DisplayName("공개 경로 /api/users/login - JWT 없이 체인 통과")
    void publicPath_login_체인통과() {
        MockServerWebExchange exchange = exchangeFor("POST", "/api/users/login");
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.filter(exchange, chainOf(chainCalled)).block();

        assertThat(chainCalled.get()).isTrue();
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    @DisplayName("공개 경로 /api/users/signup - JWT 없이 체인 통과")
    void publicPath_signup_체인통과() {
        MockServerWebExchange exchange = exchangeFor("POST", "/api/users/signup");
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.filter(exchange, chainOf(chainCalled)).block();

        assertThat(chainCalled.get()).isTrue();
    }

    @Test
    @DisplayName("공개 경로 /api/users/refresh - JWT 없이 체인 통과")
    void publicPath_refresh_체인통과() {
        MockServerWebExchange exchange = exchangeFor("POST", "/api/users/refresh");
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.filter(exchange, chainOf(chainCalled)).block();

        assertThat(chainCalled.get()).isTrue();
    }

    @Test
    @DisplayName("공개 경로 /actuator/health - JWT 없이 체인 통과")
    void publicPath_actuatorHealth_체인통과() {
        MockServerWebExchange exchange = exchangeFor("GET", "/actuator/health");
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.filter(exchange, chainOf(chainCalled)).block();

        assertThat(chainCalled.get()).isTrue();
    }

    @Test
    @DisplayName("/actuator/env 는 공개 경로 아님 - JWT 없으면 401")
    void actuatorEnv_비공개경로_401() {
        MockServerWebExchange exchange = exchangeFor("GET", "/actuator/env");
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.filter(exchange, chainOf(chainCalled)).block();

        assertThat(chainCalled.get()).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("/api/users/login-anything 은 공개 경로 아님 - JWT 없으면 401")
    void loginAnything_비공개경로_401() {
        MockServerWebExchange exchange = exchangeFor("POST", "/api/users/login-anything");
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.filter(exchange, chainOf(chainCalled)).block();

        assertThat(chainCalled.get()).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ─────────────────────────────────────────────────
    // Authorization 헤더 누락/형식 오류
    // ─────────────────────────────────────────────────

    @Test
    @DisplayName("Authorization 헤더 없음 - 401 반환, 체인 호출 안 함")
    void noAuthorizationHeader_401() {
        MockServerWebExchange exchange = exchangeFor("GET", "/api/auctions");
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.filter(exchange, chainOf(chainCalled)).block();

        assertThat(chainCalled.get()).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Bearer prefix 없는 헤더 - 401 반환")
    void noBearerPrefix_401() {
        String token = generateToken(UUID.randomUUID(), "test@example.com");
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/auctions")
                        .header("Authorization", token) // "Bearer " 없음
                        .build());
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.filter(exchange, chainOf(chainCalled)).block();

        assertThat(chainCalled.get()).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ─────────────────────────────────────────────────
    // 유효하지 않은 JWT
    // ─────────────────────────────────────────────────

    @Test
    @DisplayName("만료된 JWT - 401 반환")
    void expiredJwt_401() {
        Date now = new Date();
        String expiredToken = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .claim("email", "test@example.com")
                .issuedAt(now)
                .expiration(new Date(now.getTime() - 1000L))
                .signWith(keyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/auctions")
                        .header("Authorization", "Bearer " + expiredToken)
                        .build());
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.filter(exchange, chainOf(chainCalled)).block();

        assertThat(chainCalled.get()).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("서명이 변조된 JWT - 401 반환")
    void tamperedJwt_401() {
        String token = generateToken(UUID.randomUUID(), "test@example.com") + "tampered";
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/auctions")
                        .header("Authorization", "Bearer " + token)
                        .build());
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.filter(exchange, chainOf(chainCalled)).block();

        assertThat(chainCalled.get()).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("다른 키 쌍으로 서명된 JWT - 401 반환")
    void wrongKeyPairJwt_401() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair otherKeyPair = gen.generateKeyPair();

        Date now = new Date();
        String token = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .claim("email", "test@example.com")
                .issuedAt(now)
                .expiration(new Date(now.getTime() + 900_000L))
                .signWith(otherKeyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/auctions")
                        .header("Authorization", "Bearer " + token)
                        .build());
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.filter(exchange, chainOf(chainCalled)).block();

        assertThat(chainCalled.get()).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ─────────────────────────────────────────────────
    // 유효한 JWT → 체인 통과 + 헤더 추가
    // ─────────────────────────────────────────────────

    @Test
    @DisplayName("유효한 JWT - 체인 통과 + X-User-Id, X-User-Email 헤더 추가")
    void validJwt_체인통과_헤더추가() {
        UUID userId = UUID.randomUUID();
        String email = "test@example.com";
        String token = generateToken(userId, email);

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/auctions")
                        .header("Authorization", "Bearer " + token)
                        .build());

        AtomicReference<ServerWebExchange> captured = new AtomicReference<>();
        GatewayFilterChain chain = ex -> {
            captured.set(ex);
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isNull();
        assertThat(captured.get()).isNotNull();
        assertThat(captured.get().getRequest().getHeaders().getFirst("X-User-Id"))
                .isEqualTo(userId.toString());
        assertThat(captured.get().getRequest().getHeaders().getFirst("X-User-Email"))
                .isEqualTo(email);
    }

    @Test
    @DisplayName("유효한 JWT - email claim 없는 경우 X-User-Email 빈 문자열")
    void validJwt_email없음_빈헤더() {
        Date now = new Date();
        String token = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + 900_000L))
                .signWith(keyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/auctions")
                        .header("Authorization", "Bearer " + token)
                        .build());

        AtomicReference<ServerWebExchange> captured = new AtomicReference<>();
        GatewayFilterChain chain = ex -> { captured.set(ex); return Mono.empty(); };

        filter.filter(exchange, chain).block();

        assertThat(captured.get().getRequest().getHeaders().getFirst("X-User-Email")).isEqualTo("");
    }

    // ─────────────────────────────────────────────────
    // 헬퍼
    // ─────────────────────────────────────────────────

    private String generateToken(UUID userId, String email) {
        Date now = new Date();
        return Jwts.builder()
                .subject(userId.toString())
                .claim("email", email)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + 900_000L))
                .signWith(keyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();
    }

    private MockServerWebExchange exchangeFor(String method, String path) {
        MockServerHttpRequest request = switch (method.toUpperCase()) {
            case "POST" -> MockServerHttpRequest.post(path).build();
            default     -> MockServerHttpRequest.get(path).build();
        };
        return MockServerWebExchange.from(request);
    }

    private GatewayFilterChain chainOf(AtomicBoolean called) {
        return ex -> {
            called.set(true);
            return Mono.empty();
        };
    }
}
