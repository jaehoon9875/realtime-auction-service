package com.jaehoon.user.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaehoon.user.config.JwtProvider;
import com.jaehoon.user.dto.LoginRequest;
import com.jaehoon.user.dto.SignupRequest;
import com.jaehoon.user.dto.TokenResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 실제 PostgreSQL + Redis 컨테이너를 기동해 전체 HTTP 흐름을 검증하는 통합 테스트.
 * 단위 테스트에서 mock으로 커버하지 못하는 영역(DB 트랜잭션, Redis TTL, Flyway 마이그레이션)을 보완한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
@Testcontainers
class UserIntegrationTest {

    // RSA 키 쌍은 클래스 로딩 시 정적 초기화 (DynamicPropertySource가 BeforeAll보다 먼저 실행됨)
    private static final KeyPair KEY_PAIR = generateKeyPair();

    private static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048);
            return gen.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @SuppressWarnings("resource")
    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @SuppressWarnings("resource")
    @Container
    static GenericContainer<?> redis =
            new GenericContainer<>("redis:7-alpine")
                    .withExposedPorts(6379);

    /**
     * Testcontainers가 할당한 동적 포트/호스트를 스프링 설정으로 주입.
     * RSA 키 쌍을 Base64 인코딩하여 JWT_PRIVATE_KEY, JWT_PUBLIC_KEY로 주입.
     * Redis는 비밀번호 없이 기동하므로 password를 빈 문자열로 오버라이드.
     */
    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("spring.data.redis.password", () -> "");
        registry.add("JWT_PRIVATE_KEY",
                () -> Base64.getEncoder().encodeToString(KEY_PAIR.getPrivate().getEncoded()));
        registry.add("JWT_PUBLIC_KEY",
                () -> Base64.getEncoder().encodeToString(KEY_PAIR.getPublic().getEncoded()));
    }

    @Autowired MockMvc mockMvc;
    @Autowired JwtProvider jwtProvider;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String testEmail;

    @BeforeEach
    void setUp() {
        testEmail = "user-" + System.nanoTime() + "@example.com";
    }

    // ─────────────────────────────────────────────────
    // 핵심 플로우: signup → login → me → refresh → logout
    // ─────────────────────────────────────────────────

    @Test
    @DisplayName("전체 인증 플로우 - signup → login → me → refresh → logout → refresh 재시도")
    void 전체인증플로우() throws Exception {
        // 1. 회원가입
        signup(testEmail, "password123", "닉네임");

        // 2. 로그인 → 토큰 발급
        TokenResponse tokens = login(testEmail, "password123");
        assertThat(tokens.accessToken()).isNotBlank();
        assertThat(tokens.refreshToken()).isNotBlank();

        // 3. 내 정보 조회 (Gateway가 Access Token 검증 후 X-User-Id 주입)
        UUID userId = jwtProvider.extractUserId(tokens.accessToken());
        mockMvc.perform(get("/users/me")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(testEmail));

        // 4. Refresh Token으로 토큰 재발급 (Rotation)
        TokenResponse newTokens = refresh(tokens.refreshToken());
        assertThat(newTokens.refreshToken()).isNotEqualTo(tokens.refreshToken());

        // 5. 로그아웃: Gateway가 X-User-Id 주입 → userId 기준으로 Redis Refresh Token 삭제
        UUID newUserId = jwtProvider.extractUserId(newTokens.accessToken());
        mockMvc.perform(post("/users/logout")
                        .header("X-User-Id", newUserId.toString()))
                .andExpect(status().isNoContent());

        // 6. 로그아웃 후 Refresh Token으로 재발급 시도 → 400 (Redis에서 해당 userId 키 삭제됨)
        mockMvc.perform(post("/users/refresh")
                        .header("Authorization", "Bearer " + newTokens.refreshToken()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("이메일 중복 가입 - 400 Bad Request")
    void 이메일중복가입_400() throws Exception {
        signup(testEmail, "password123", "닉네임");

        mockMvc.perform(post("/users/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new SignupRequest(testEmail, "password123", "닉네임2"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("Refresh Token Rotation - 이전 Refresh Token은 재사용 불가 (400)")
    void refreshToken_Rotation_이전토큰_재사용불가() throws Exception {
        signup(testEmail, "password123", "닉네임");
        TokenResponse first = login(testEmail, "password123");

        // 첫 번째 갱신 (old → new)
        refresh(first.refreshToken());

        // 이미 사용된 old refresh token으로 재시도 → 탈취 감지 → 400
        mockMvc.perform(post("/users/refresh")
                        .header("Authorization", "Bearer " + first.refreshToken()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("존재하지 않는 Refresh Token으로 갱신 시도 - 400")
    void refresh_존재하지않는토큰_400() throws Exception {
        // 유효한 서명의 Refresh JWT를 임시 JwtProvider로 생성하되, Redis에는 존재하지 않음
        String encodedPrivate = Base64.getEncoder().encodeToString(KEY_PAIR.getPrivate().getEncoded());
        String encodedPublic  = Base64.getEncoder().encodeToString(KEY_PAIR.getPublic().getEncoded());
        JwtProvider tempProvider = new JwtProvider(encodedPrivate, encodedPublic, 900_000L, 604_800_000L);
        String unknownToken = tempProvider.generateRefreshToken(UUID.randomUUID());

        mockMvc.perform(post("/users/refresh")
                        .header("Authorization", "Bearer " + unknownToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("잘못된 비밀번호로 로그인 - 400 Bad Request")
    void login_잘못된비밀번호_400() throws Exception {
        signup(testEmail, "password123", "닉네임");

        mockMvc.perform(post("/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest(testEmail, "wrongpassword"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("X-User-Id 헤더 없음 - 401 Unauthorized (Gateway 미통과)")
    void me_XUserId없음_401() throws Exception {
        // JWT 검증·만료 체크는 Gateway 책임. user-service는 X-User-Id 헤더 부재 시 401 반환.
        mockMvc.perform(get("/users/me"))
                .andExpect(status().isUnauthorized());
    }

    // ─────────────────────────────────────────────────
    // 헬퍼
    // ─────────────────────────────────────────────────

    private void signup(String email, String password, String nickname) throws Exception {
        mockMvc.perform(post("/users/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new SignupRequest(email, password, nickname))))
                .andExpect(status().isCreated());
    }

    private TokenResponse login(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest(email, password))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), TokenResponse.class);
    }

    private TokenResponse refresh(String refreshToken) throws Exception {
        MvcResult result = mockMvc.perform(post("/users/refresh")
                        .header("Authorization", "Bearer " + refreshToken))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), TokenResponse.class);
    }
}
