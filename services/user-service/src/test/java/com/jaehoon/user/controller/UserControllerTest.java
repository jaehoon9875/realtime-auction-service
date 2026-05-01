package com.jaehoon.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaehoon.user.config.JwtAuthFilter;
import com.jaehoon.user.config.JwtProvider;
import com.jaehoon.user.config.SecurityConfig;
import com.jaehoon.user.dto.LoginRequest;
import com.jaehoon.user.dto.SignupRequest;
import com.jaehoon.user.dto.TokenResponse;
import com.jaehoon.user.dto.UserResponse;
import com.jaehoon.user.exception.EmailAlreadyExistsException;
import com.jaehoon.user.exception.GlobalExceptionHandler;
import com.jaehoon.user.exception.InvalidCredentialsException;
import com.jaehoon.user.exception.InvalidTokenException;
import com.jaehoon.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * HTTP 계약 검증: 상태 코드, 응답 포맷, Bean Validation, 인증/인가 동작.
 *
 * - JWT 파싱 동작은 JwtProviderTest / JwtAuthGlobalFilterTest에서 별도 검증하므로,
 *   이 테스트의 JwtAuthFilter는 "그냥 통과"하는 passthrough 구현체를 사용한다.
 * - 인증이 필요한 엔드포인트(@WithMockUser)와 permitAll 경로를 SecurityConfig 설정 그대로 검증한다.
 */
@WebMvcTest(UserController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, UserControllerTest.TestConfig.class})
@TestPropertySource(properties = {
        // application.yml의 ${JWT_SECRET} 플레이스홀더 해소
        "JWT_SECRET=test-secret-key-must-be-at-least-256-bits-long-for-hs256!!",
        "app.jwt.secret=test-secret-key-must-be-at-least-256-bits-long-for-hs256!!",
        "app.jwt.access-token-expiration-ms=900000",
        "app.jwt.refresh-token-expiration-ms=604800000"
})
class UserControllerTest {

    @Autowired MockMvc mockMvc;

    // Spring Boot 4 @WebMvcTest 슬라이스에는 ObjectMapper 빈이 기본 포함되지 않음
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** UserServiceMockConfig에서 등록한 Mockito mock 인스턴스 */
    @Autowired UserService userService;

    @BeforeEach
    void resetMocks() {
        reset(userService);
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        UserService userService() {
            return mock(UserService.class);
        }

        /**
         * SecurityConfig 생성자는 JwtAuthFilter를 요구한다.
         * 이 테스트에서 JWT 파싱 검증은 목적이 아니므로,
         * 아무 처리 없이 체인을 통과시키는 passthrough 구현체를 제공한다.
         */
        @Bean
        JwtAuthFilter jwtAuthFilter(JwtProvider jwtProvider) {
            return new JwtAuthFilter(jwtProvider) {
                @Override
                protected void doFilterInternal(HttpServletRequest req,
                                                HttpServletResponse res,
                                                FilterChain chain) throws ServletException, java.io.IOException {
                    chain.doFilter(req, res);
                }
            };
        }
    }

    // ─────────────────────────────────────────────────
    // POST /users/signup
    // ─────────────────────────────────────────────────

    @Test
    @DisplayName("signup 정상 - 201 Created")
    void signup_정상_201() throws Exception {
        willDoNothing().given(userService).signup(any());
        SignupRequest body = new SignupRequest("new@example.com", "password123", "닉네임");

        mockMvc.perform(post("/users/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("signup 이메일 형식 오류 - 400 Bad Request")
    void signup_이메일형식오류_400() throws Exception {
        SignupRequest body = new SignupRequest("not-an-email", "password123", "닉네임");

        mockMvc.perform(post("/users/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("signup 비밀번호 8자 미만 - 400 Bad Request")
    void signup_비밀번호짧음_400() throws Exception {
        SignupRequest body = new SignupRequest("test@example.com", "short", "닉네임");

        mockMvc.perform(post("/users/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("signup 닉네임 빈 값 - 400 Bad Request")
    void signup_닉네임빈값_400() throws Exception {
        SignupRequest body = new SignupRequest("test@example.com", "password123", "");

        mockMvc.perform(post("/users/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("signup 이메일 중복 - 409 Conflict")
    void signup_이메일중복_409() throws Exception {
        willThrow(new EmailAlreadyExistsException("dup@example.com"))
                .given(userService).signup(any());
        SignupRequest body = new SignupRequest("dup@example.com", "password123", "닉네임");

        mockMvc.perform(post("/users/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").exists());
    }

    // ─────────────────────────────────────────────────
    // POST /users/login
    // ─────────────────────────────────────────────────

    @Test
    @DisplayName("login 정상 - 200 OK + accessToken/refreshToken 포함")
    void login_정상_200() throws Exception {
        TokenResponse token = new TokenResponse("access-jwt", "refresh-uuid");
        given(userService.login(any())).willReturn(token);
        LoginRequest body = new LoginRequest("test@example.com", "password123");

        mockMvc.perform(post("/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-jwt"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-uuid"));
    }

    @Test
    @DisplayName("login 자격증명 오류 - 401 Unauthorized")
    void login_자격증명오류_401() throws Exception {
        given(userService.login(any())).willThrow(new InvalidCredentialsException());
        LoginRequest body = new LoginRequest("test@example.com", "wrong");

        mockMvc.perform(post("/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("login 이메일 형식 오류 - 400 Bad Request")
    void login_이메일형식오류_400() throws Exception {
        LoginRequest body = new LoginRequest("not-email", "password123");

        mockMvc.perform(post("/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    // ─────────────────────────────────────────────────
    // POST /users/refresh  (공개 경로)
    // ─────────────────────────────────────────────────

    @Test
    @DisplayName("refresh 정상 - 200 OK + 신규 토큰 반환")
    void refresh_정상_200() throws Exception {
        TokenResponse newToken = new TokenResponse("new-access", "new-refresh");
        given(userService.refresh(anyString())).willReturn(newToken);

        mockMvc.perform(post("/users/refresh")
                        .header("Authorization", "Bearer old-refresh-uuid"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new-access"))
                .andExpect(jsonPath("$.refreshToken").value("new-refresh"));
    }

    @Test
    @DisplayName("refresh 유효하지 않은 토큰 - 401 Unauthorized")
    void refresh_유효하지않은토큰_401() throws Exception {
        given(userService.refresh(anyString()))
                .willThrow(new InvalidTokenException("만료되었거나 유효하지 않은 Refresh Token입니다"));

        mockMvc.perform(post("/users/refresh")
                        .header("Authorization", "Bearer expired-token"))
                .andExpect(status().isUnauthorized());
    }

    // ─────────────────────────────────────────────────
    // POST /users/logout  (인증 필요)
    // ─────────────────────────────────────────────────

    @Test
    @DisplayName("logout 인증된 사용자 - 204 No Content")
    void logout_인증된사용자_204() throws Exception {
        // .with(user(...))로 요청 처리 시점에 SecurityContext 주입 (STATELESS 설정에서도 안전)
        // Authorization 헤더에 refresh token을 담아 전달 → controller가 추출해 service.logout() 호출
        willDoNothing().given(userService).logout(anyString());

        mockMvc.perform(post("/users/logout")
                        .with(user("someUser"))
                        .header("Authorization", "Bearer some-refresh-token"))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("logout Authorization 헤더 없음 - 403 Forbidden (인증 미통과)")
    void logout_인증없음_403() throws Exception {
        // /users/logout은 permitAll 아님 → 인증 없이 접근 시 스프링 시큐리티가 403 반환
        mockMvc.perform(post("/users/logout"))
                .andExpect(status().isForbidden());
    }

    // ─────────────────────────────────────────────────
    // GET /users/me  (인증 필요)
    // ─────────────────────────────────────────────────

    @Test
    @DisplayName("me 인증된 사용자 - 200 OK + UserResponse 반환")
    void me_인증된사용자_200() throws Exception {
        // .with(user(uuid))로 요청 처리 시점에 SecurityContext 주입 (STATELESS 설정에서도 안전)
        // username = userId → 컨트롤러의 userDetails.getUsername()으로 사용됨
        UUID userId = UUID.fromString("ba68fda7-767b-468d-b722-27d913bf48b5");
        String email = "test@example.com";
        UserResponse response = new UserResponse(userId, email, "닉네임");
        given(userService.me(userId)).willReturn(response);

        mockMvc.perform(get("/users/me")
                        .with(user(userId.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId.toString()))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.nickname").value("닉네임"));
    }

    @Test
    @DisplayName("me Authorization 헤더 없음 - 403 Forbidden")
    void me_인증없음_403() throws Exception {
        mockMvc.perform(get("/users/me"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("me 변조된 JWT - 403 Forbidden (SecurityContext 비워짐)")
    void me_변조된JWT_403() throws Exception {
        mockMvc.perform(get("/users/me")
                        .header("Authorization", "Bearer invalid.jwt.token"))
                .andExpect(status().isForbidden());
    }
}
