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
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class UserService {

    // Redis 키: refresh_token:{userId} → refreshToken 문자열
    private static final String REFRESH_TOKEN_KEY_PREFIX = "refresh_token:";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final StringRedisTemplate redisTemplate;

    // 회원가입: 이메일 중복 확인 → BCrypt 해싱 → 저장
    @Transactional
    public void signup(SignupRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new EmailAlreadyExistsException(request.email());
        }

        User user = User.builder()
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .nickname(request.nickname())
                .build();

        userRepository.save(user);
    }

    // 로그인: 이메일/비밀번호 검증 → Access + Refresh Token 발급 → Redis 저장
    public TokenResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new InvalidCredentialsException();
        }

        return issueTokens(user);
    }

    // Refresh Token Rotation
    //   1. Refresh JWT 파싱 → userId 추출 (서명·만료 검증 포함)
    //   2. Redis에서 refresh_token:{userId} 조회
    //   3. null → 세션 만료 또는 이미 무효화 → 예외
    //   4. 불일치 → 탈취 감지 → userId 키 삭제(전체 세션 무효화) → 예외
    //   5. 일치 → 신규 토큰 발급, Redis 덮어쓰기
    public TokenResponse refresh(String refreshToken) {
        // Refresh Token JWT에서 userId 추출 (서명 위변조·만료 시 InvalidTokenException)
        UUID userId;
        try {
            userId = jwtProvider.extractUserId(refreshToken);
        } catch (InvalidTokenException e) {
            throw new InvalidTokenException("유효하지 않은 Refresh Token입니다");
        }

        // Redis에서 현재 userId에 저장된 Refresh Token 조회
        String storedToken = redisTemplate.opsForValue().get(REFRESH_TOKEN_KEY_PREFIX + userId);

        if (storedToken == null) {
            // TTL 만료 또는 로그아웃으로 이미 무효화된 세션
            throw new InvalidTokenException("만료되었거나 유효하지 않은 Refresh Token입니다");
        }

        if (!storedToken.equals(refreshToken)) {
            // 탈취 감지: 서명은 유효하지만 Redis 저장값과 다름 → 이전 토큰 재사용 의심
            // 해당 userId의 세션 전체를 무효화하여 피해 확산 방지
            redisTemplate.delete(REFRESH_TOKEN_KEY_PREFIX + userId);
            throw new InvalidTokenException("보안을 위해 세션이 무효화되었습니다. 다시 로그인해주세요");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        // 기존 토큰 삭제 후 신규 발급 (Rotation)
        redisTemplate.delete(REFRESH_TOKEN_KEY_PREFIX + userId);
        return issueTokens(user);
    }

    // 로그아웃: userId 기준으로 Redis 키 삭제 → 해당 사용자의 세션 전체 무효화
    public void logout(UUID userId) {
        redisTemplate.delete(REFRESH_TOKEN_KEY_PREFIX + userId);
    }

    // 내 정보 조회 (클래스 레벨 readOnly = true 상속)
    public UserResponse me(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        return new UserResponse(user.getId(), user.getEmail(), user.getNickname());
    }

    // Access Token + Refresh Token 발급 후 Redis에 userId 기준으로 저장
    private TokenResponse issueTokens(User user) {
        String accessToken = jwtProvider.generateAccessToken(user.getId(), user.getEmail());
        String refreshToken = jwtProvider.generateRefreshToken(user.getId());

        // Redis 키: refresh_token:{userId} → refreshToken (TTL 7일)
        // 같은 userId로 재로그인 시 덮어쓰기 → 이전 Refresh Token 자동 무효화
        Duration ttl = Duration.ofMillis(jwtProvider.getRefreshTokenExpirationMs());
        redisTemplate.opsForValue().set(
                REFRESH_TOKEN_KEY_PREFIX + user.getId(),
                refreshToken,
                ttl
        );

        return new TokenResponse(accessToken, refreshToken);
    }
}
