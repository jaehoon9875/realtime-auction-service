package com.jaehoon.user.service;

import com.jaehoon.user.config.JwtProvider;
import com.jaehoon.user.dto.LoginRequest;
import com.jaehoon.user.dto.SignupRequest;
import com.jaehoon.user.dto.TokenResponse;
import com.jaehoon.user.dto.UserResponse;
import com.jaehoon.user.entity.User;
import com.jaehoon.user.exception.EmailAlreadyExistsException;
import com.jaehoon.user.exception.InvalidCredentialsException;
import com.jaehoon.user.exception.InvalidTokenException;
import com.jaehoon.user.exception.UserNotFoundException;
import com.jaehoon.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @InjectMocks
    private UserService userService;

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtProvider jwtProvider;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;

    private static final String REFRESH_KEY_PREFIX = "refresh_token:";

    // ─────────────────────────────────────────────────
    // signup
    // ─────────────────────────────────────────────────

    @Test
    @DisplayName("signup 정상 - BCrypt 인코딩 후 User 저장")
    void signup_정상() {
        SignupRequest request = new SignupRequest("test@example.com", "password123", "닉네임");
        given(userRepository.existsByEmail(request.email())).willReturn(false);
        given(passwordEncoder.encode(request.password())).willReturn("$2a$hashed");

        assertThatCode(() -> userService.signup(request)).doesNotThrowAnyException();

        then(userRepository).should().save(any(User.class));
        then(passwordEncoder).should().encode("password123");
    }

    @Test
    @DisplayName("signup 이메일 중복 - EmailAlreadyExistsException, save 호출 없음")
    void signup_이메일중복() {
        SignupRequest request = new SignupRequest("dup@example.com", "password123", "닉네임");
        given(userRepository.existsByEmail(request.email())).willReturn(true);

        assertThatThrownBy(() -> userService.signup(request))
                .isInstanceOf(EmailAlreadyExistsException.class)
                .hasMessage("이미 사용 중인 이메일입니다");

        then(userRepository).should(never()).save(any());
    }

    // ─────────────────────────────────────────────────
    // login
    // ─────────────────────────────────────────────────

    @Test
    @DisplayName("login 정상 - Access + Refresh Token 발급, Redis에 refresh_token:{userId} 저장")
    void login_정상() {
        UUID userId = UUID.randomUUID();
        User user = buildUser(userId, "test@example.com", "encoded");
        LoginRequest request = new LoginRequest("test@example.com", "password123");

        given(userRepository.findByEmail(request.email())).willReturn(Optional.of(user));
        given(passwordEncoder.matches(request.password(), "encoded")).willReturn(true);
        given(jwtProvider.generateAccessToken(userId, "test@example.com")).willReturn("access-jwt");
        given(jwtProvider.generateRefreshToken(userId)).willReturn("refresh-jwt");
        given(jwtProvider.getRefreshTokenExpirationMs()).willReturn(604_800_000L);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);

        TokenResponse result = userService.login(request);

        assertThat(result.accessToken()).isEqualTo("access-jwt");
        assertThat(result.refreshToken()).isEqualTo("refresh-jwt");
        // Redis 키: refresh_token:{userId} → refreshToken 값 (userId 기준 단일 세션)
        then(valueOperations).should().set(
                eq(REFRESH_KEY_PREFIX + userId),
                eq("refresh-jwt"),
                any(Duration.class)
        );
    }

    @Test
    @DisplayName("login 이메일 없음 - InvalidCredentialsException")
    void login_이메일없음() {
        given(userRepository.findByEmail(anyString())).willReturn(Optional.empty());

        assertThatThrownBy(() -> userService.login(new LoginRequest("none@example.com", "pw")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    @DisplayName("login 비밀번호 불일치 - InvalidCredentialsException")
    void login_비밀번호불일치() {
        UUID userId = UUID.randomUUID();
        User user = buildUser(userId, "test@example.com", "encoded");
        given(userRepository.findByEmail("test@example.com")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("wrong", "encoded")).willReturn(false);

        assertThatThrownBy(() -> userService.login(new LoginRequest("test@example.com", "wrong")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    // ─────────────────────────────────────────────────
    // refresh
    // ─────────────────────────────────────────────────

    @Test
    @DisplayName("refresh 정상 - 기존 Refresh Token 삭제 후 신규 토큰 발급 (Rotation)")
    void refresh_정상() {
        String oldRefresh = "old-refresh-jwt";
        UUID userId = UUID.randomUUID();
        User user = buildUser(userId, "test@example.com", "encoded");

        given(jwtProvider.extractUserId(oldRefresh)).willReturn(userId);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        // Redis에서 userId 키로 저장된 Refresh Token이 요청 토큰과 일치
        given(valueOperations.get(REFRESH_KEY_PREFIX + userId)).willReturn(oldRefresh);
        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(jwtProvider.generateAccessToken(userId, "test@example.com")).willReturn("new-access");
        given(jwtProvider.generateRefreshToken(userId)).willReturn("new-refresh");
        given(jwtProvider.getRefreshTokenExpirationMs()).willReturn(604_800_000L);

        TokenResponse result = userService.refresh(oldRefresh);

        assertThat(result.accessToken()).isEqualTo("new-access");
        assertThat(result.refreshToken()).isEqualTo("new-refresh");
        // 기존 Refresh Token 반드시 삭제 (Rotation 핵심)
        then(redisTemplate).should().delete(REFRESH_KEY_PREFIX + userId);
    }

    @Test
    @DisplayName("refresh - 유효하지 않은 JWT → InvalidTokenException")
    void refresh_유효하지않은JWT() {
        given(jwtProvider.extractUserId("invalid-token"))
                .willThrow(new InvalidTokenException("유효하지 않은 토큰입니다"));

        assertThatThrownBy(() -> userService.refresh("invalid-token"))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    @DisplayName("refresh - Redis에 토큰 없음 (만료 or 로그아웃) → InvalidTokenException")
    void refresh_Redis없는토큰() {
        UUID userId = UUID.randomUUID();
        given(jwtProvider.extractUserId("expired-token")).willReturn(userId);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(REFRESH_KEY_PREFIX + userId)).willReturn(null);

        assertThatThrownBy(() -> userService.refresh("expired-token"))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    @DisplayName("refresh - 탈취 감지: 서명 유효하지만 Redis 저장값과 다른 토큰 → 세션 전체 무효화")
    void refresh_탈취감지() {
        UUID userId = UUID.randomUUID();
        String storedToken  = "stored-refresh-jwt";    // Redis에 저장된 현재 유효 토큰
        String attackerToken = "attacker-refresh-jwt"; // 공격자가 재사용 시도하는 이전 토큰

        // 공격자 토큰도 JWT 서명 자체는 통과하지만 Redis 값과 다름
        given(jwtProvider.extractUserId(attackerToken)).willReturn(userId);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(REFRESH_KEY_PREFIX + userId)).willReturn(storedToken);

        assertThatThrownBy(() -> userService.refresh(attackerToken))
                .isInstanceOf(InvalidTokenException.class);

        // 탈취 감지 → userId 기준 세션 전체 무효화
        then(redisTemplate).should().delete(REFRESH_KEY_PREFIX + userId);
    }

    @Test
    @DisplayName("refresh - Redis에 토큰 있으나 User 삭제된 경우 → UserNotFoundException")
    void refresh_User삭제됨() {
        UUID userId = UUID.randomUUID();
        String refreshToken = "some-refresh-jwt";

        given(jwtProvider.extractUserId(refreshToken)).willReturn(userId);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(REFRESH_KEY_PREFIX + userId)).willReturn(refreshToken);
        given(userRepository.findById(userId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> userService.refresh(refreshToken))
                .isInstanceOf(UserNotFoundException.class);
    }

    // ─────────────────────────────────────────────────
    // logout
    // ─────────────────────────────────────────────────

    @Test
    @DisplayName("logout - Redis에서 refresh_token:{userId} 키 삭제")
    void logout_정상() {
        UUID userId = UUID.randomUUID();

        userService.logout(userId);

        then(redisTemplate).should().delete(REFRESH_KEY_PREFIX + userId);
    }

    @Test
    @DisplayName("logout - 이미 로그아웃된 사용자여도 예외 없이 처리 (멱등성)")
    void logout_멱등성() {
        UUID userId = UUID.randomUUID();
        // Redis delete는 키가 없어도 예외를 던지지 않음
        assertThatCode(() -> userService.logout(userId)).doesNotThrowAnyException();
    }

    // ─────────────────────────────────────────────────
    // me
    // ─────────────────────────────────────────────────

    @Test
    @DisplayName("me 정상 - UserResponse 반환")
    void me_정상() {
        UUID userId = UUID.randomUUID();
        User user = buildUser(userId, "test@example.com", "encoded");
        given(userRepository.findById(userId)).willReturn(Optional.of(user));

        UserResponse result = userService.me(userId);

        assertThat(result.id()).isEqualTo(userId);
        assertThat(result.email()).isEqualTo("test@example.com");
        assertThat(result.nickname()).isEqualTo("닉네임");
    }

    @Test
    @DisplayName("me 사용자 없음 - UserNotFoundException")
    void me_사용자없음() {
        UUID userId = UUID.randomUUID();
        given(userRepository.findById(userId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> userService.me(userId))
                .isInstanceOf(UserNotFoundException.class);
    }

    // ─────────────────────────────────────────────────
    // 헬퍼
    // ─────────────────────────────────────────────────

    private User buildUser(UUID id, String email, String encodedPassword) {
        User user = User.builder()
                .email(email)
                .password(encodedPassword)
                .nickname("닉네임")
                .build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
