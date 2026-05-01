package com.jaehoon.user.controller;

import com.jaehoon.user.dto.LoginRequest;
import com.jaehoon.user.dto.SignupRequest;
import com.jaehoon.user.dto.TokenResponse;
import com.jaehoon.user.dto.UserResponse;
import com.jaehoon.user.exception.InvalidTokenException;
import com.jaehoon.user.service.UserService;
import com.jaehoon.user.util.BearerTokenExtractor;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping("/signup")
    public ResponseEntity<Void> signup(@Valid @RequestBody SignupRequest request) {
        userService.signup(request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(userService.login(request));
    }

    // Refresh Token은 Authorization 헤더 또는 Body로 받을 수 있음
    // 여기서는 Authorization: Bearer {refreshToken} 방식 사용
    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(
            @RequestHeader("Authorization") String bearerToken) {
        String refreshToken = extractBearerToken(bearerToken);
        return ResponseEntity.ok(userService.refresh(refreshToken));
    }

    // 로그아웃: Refresh Token으로 Redis 키 삭제
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @RequestHeader("Authorization") String bearerToken) {
        String refreshToken = extractBearerToken(bearerToken);
        userService.logout(refreshToken);
        return ResponseEntity.noContent().build();
    }

    // 인증된 사용자만 접근 가능 (JwtAuthFilter에서 SecurityContext에 userId 주입)
    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(@AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        return ResponseEntity.ok(userService.me(userId));
    }

    private String extractBearerToken(String header) {
        String token = BearerTokenExtractor.extract(header);
        if (token == null) {
            throw new InvalidTokenException("Authorization 헤더 형식이 올바르지 않습니다");
        }
        return token;
    }
}
