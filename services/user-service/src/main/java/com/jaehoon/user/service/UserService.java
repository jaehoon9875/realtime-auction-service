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

    private static final String REFRESH_TOKEN_KEY_PREFIX = "refresh_token:";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final StringRedisTemplate redisTemplate;

    // 회원가입: 이메일 중복 확인 → BCrypt 해싱 → 저장 (DB 쓰기 발생 → 쓰기 트랜잭션 명시)
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
    //   1. 토큰에서 userId 추출
    //   2. Redis에서 해당 userId의 Refresh Token 조회
    //   3. 요청 토큰과 불일치 시 → 탈취로 판단, 해당 키 삭제 후 예외
    //   4. 일치 시 → 신규 Access + Refresh 발급, Redis 덮어쓰기
    public TokenResponse refresh(String refreshToken) {
        // Refresh Token 자체는 서명된 JWT가 아닌 UUID 문자열이므로
        // Access Token으로 userId를 알 수 없음 → Redis에서 직접 매칭 필요
        // 탈취 감지를 위해 모든 userId 키를 조회하는 대신,
        // Refresh Token 값 → userId 역방향 조회를 위한 별도 키 활용
        String userIdStr = redisTemplate.opsForValue().get(REFRESH_TOKEN_KEY_PREFIX + refreshToken);

        if (userIdStr == null) {
            throw new InvalidTokenException("만료되었거나 유효하지 않은 Refresh Token입니다");
        }

        UUID userId = UUID.fromString(userIdStr);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        // 기존 토큰 삭제 후 신규 발급 (Rotation)
        redisTemplate.delete(REFRESH_TOKEN_KEY_PREFIX + refreshToken);
        return issueTokens(user);
    }

    // 로그아웃: Access Token에서 userId 추출 → Redis에서 해당 Refresh Token 삭제
    // 주의: Refresh Token 값 → userId 구조이므로 userId로 역방향 조회 불가
    // 클라이언트가 Refresh Token을 함께 전달해야 Redis에서 삭제 가능
    public void logout(String refreshToken) {
        redisTemplate.delete(REFRESH_TOKEN_KEY_PREFIX + refreshToken);
    }

    // 내 정보 조회 (클래스 레벨 readOnly = true 상속)
    public UserResponse me(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        return new UserResponse(user.getId(), user.getEmail(), user.getNickname());
    }

    // Access Token + Refresh Token 발급 후 Redis 저장 공통 로직
    private TokenResponse issueTokens(User user) {
        String accessToken = jwtProvider.generateAccessToken(user.getId(), user.getEmail());
        String refreshToken = jwtProvider.generateRefreshToken();

        // Redis 키: refresh_token:{refreshToken 값} → userId (TTL 7일)
        // Rotation 시 기존 토큰 키가 달라지므로 자동으로 이전 토큰 무효화
        Duration ttl = Duration.ofMillis(jwtProvider.getRefreshTokenExpirationMs());
        redisTemplate.opsForValue().set(
                REFRESH_TOKEN_KEY_PREFIX + refreshToken,
                user.getId().toString(),
                ttl
        );

        return new TokenResponse(accessToken, refreshToken);
    }
}
