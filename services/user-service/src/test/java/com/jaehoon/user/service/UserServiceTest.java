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
                .hasMessageContaining("dup@example.com");

        then(userRepository).should(never()).save(any());
    }

    // ─────────────────────────────────────────────────
    // login
    // ─────────────────────────────────────────────────

    @Test
    @DisplayName("login 정상 - Access + Refresh Token 발급, Redis에 Refresh Token 저장")
    void login_정상() {
        UUID userId = UUID.randomUUID();
        User user = buildUser(userId, "test@example.com", "encoded");
        LoginRequest request = new LoginRequest("test@example.com", "password123");

        given(userRepository.findByEmail(request.email())).willReturn(Optional.of(user));
        given(passwordEncoder.matches(request.password(), "encoded")).willReturn(true);
        given(jwtProvider.generateAccessToken(userId, "test@example.com")).willReturn("access-jwt");
        given(jwtProvider.generateRefreshToken()).willReturn("refresh-uuid");
        given(jwtProvider.getRefreshTokenExpirationMs()).willReturn(604_800_000L);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);

        TokenResponse result = userService.login(request);

        assertThat(result.accessToken()).isEqualTo("access-jwt");
        assertThat(result.refreshToken()).isEqualTo("refresh-uuid");
        // Redis에 refresh_token:{refreshToken} → userId 저장 확인
        then(valueOperations).should().set(
                eq(REFRESH_KEY_PREFIX + "refresh-uuid"),
                eq(userId.toString()),
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
        String oldRefresh = "old-refresh-uuid";
        UUID userId = UUID.randomUUID();
        User user = buildUser(userId, "test@example.com", "encoded");

        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(REFRESH_KEY_PREFIX + oldRefresh)).willReturn(userId.toString());
        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(jwtProvider.generateAccessToken(userId, "test@example.com")).willReturn("new-access");
        given(jwtProvider.generateRefreshToken()).willReturn("new-refresh");
        given(jwtProvider.getRefreshTokenExpirationMs()).willReturn(604_800_000L);

        TokenResponse result = userService.refresh(oldRefresh);

        assertThat(result.accessToken()).isEqualTo("new-access");
        assertThat(result.refreshToken()).isEqualTo("new-refresh");
        // 기존 Refresh Token 반드시 삭제 (Rotation 핵심)
        then(redisTemplate).should().delete(REFRESH_KEY_PREFIX + oldRefresh);
    }

    @Test
    @DisplayName("refresh Redis에 토큰 없음 (만료 or 탈취) - InvalidTokenException")
    void refresh_Redis없는토큰() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(anyString())).willReturn(null);

        assertThatThrownBy(() -> userService.refresh("non-existent-token"))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    @DisplayName("refresh Redis에 토큰 있으나 User 삭제된 경우 - UserNotFoundException")
    void refresh_User삭제됨() {
        UUID userId = UUID.randomUUID();
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(REFRESH_KEY_PREFIX + "token")).willReturn(userId.toString());
        given(userRepository.findById(userId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> userService.refresh("token"))
                .isInstanceOf(UserNotFoundException.class);
    }

    // ─────────────────────────────────────────────────
    // logout
    // ─────────────────────────────────────────────────

    @Test
    @DisplayName("logout - Redis에서 refresh_token:{token} 키 삭제")
    void logout_정상() {
        String refreshToken = "some-refresh-token";

        userService.logout(refreshToken);

        then(redisTemplate).should().delete(REFRESH_KEY_PREFIX + refreshToken);
    }

    @Test
    @DisplayName("logout - 존재하지 않는 토큰이어도 예외 없이 처리 (멱등성)")
    void logout_존재하지않는토큰_예외없음() {
        // Redis delete는 키가 없어도 예외를 던지지 않음
        assertThatCode(() -> userService.logout("ghost-token")).doesNotThrowAnyException();
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

    /**
     * User 엔티티는 @NoArgsConstructor(PROTECTED)이고 id는 DB가 생성하므로
     * ReflectionTestUtils로 id를 주입해 테스트용 객체 구성
     */
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
