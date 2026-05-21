package com.jaehoon.user.controller;

import com.jaehoon.user.dto.LoginRequest;
import com.jaehoon.user.dto.SignupRequest;
import com.jaehoon.user.dto.TokenResponse;
import com.jaehoon.user.dto.UserResponse;
import com.jaehoon.user.exception.InvalidTokenException;
import com.jaehoon.user.service.UserService;
import com.jaehoon.user.util.BearerTokenExtractor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * 회원가입, 로그인, JWT 토큰 관리 REST API.
 */
@Tag(name = "User", description = "회원가입, 로그인, 토큰 관리 API")
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * 이메일·비밀번호로 회원가입한다.
     *
     * @param request 가입 요청 본문
     * @return HTTP 201 (본문 없음)
     * @throws com.jaehoon.user.exception.EmailAlreadyExistsException 이미 등록된 이메일
     */
    @Operation(summary = "회원가입")
    @ApiResponse(responseCode = "201", description = "가입 성공")
    @ApiResponse(responseCode = "400", description = "이미 존재하는 이메일")
    @PostMapping("/signup")
    public ResponseEntity<Void> signup(@Valid @RequestBody SignupRequest request) {
        userService.signup(request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /**
     * 이메일·비밀번호로 로그인하고 Access·Refresh Token을 발급한다.
     *
     * @param request 로그인 요청 본문
     * @return 발급된 토큰 쌍
     * @throws com.jaehoon.user.exception.InvalidCredentialsException 이메일 또는 비밀번호 불일치
     */
    @Operation(summary = "로그인")
    @ApiResponse(responseCode = "200", description = "토큰 발급")
    @ApiResponse(responseCode = "400", description = "이메일 또는 비밀번호 불일치")
    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(userService.login(request));
    }

    /**
     * Refresh Token으로 새 Access Token을 발급한다 (Rotation).
     *
     * @param bearerToken {@code Authorization: Bearer {refreshToken}} 헤더
     * @return 새 Access Token 및 Refresh Token
     * @throws com.jaehoon.user.exception.InvalidTokenException 토큰 형식 오류, 만료, 탈취 감지 시
     */
    @Operation(summary = "Access Token 재발급")
    @ApiResponse(responseCode = "200", description = "새 Access Token 발급")
    @ApiResponse(responseCode = "400", description = "Refresh Token 만료 또는 유효하지 않음")
    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(
            @RequestHeader("Authorization") String bearerToken) {
        String refreshToken = extractBearerToken(bearerToken);
        return ResponseEntity.ok(userService.refresh(refreshToken));
    }

    /**
     * 로그아웃하고 Redis에 저장된 Refresh Token을 무효화한다.
     *
     * @param jwt Access Token principal (사용자 ID)
     * @return HTTP 204
     */
    @Operation(summary = "로그아웃", security = @SecurityRequirement(name = "BearerAuth"))
    @ApiResponse(responseCode = "204", description = "로그아웃 성공")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        userService.logout(userId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 인증된 사용자의 프로필 정보를 조회한다.
     *
     * @param jwt Access Token principal (사용자 ID)
     * @return 사용자 정보
     */
    @Operation(summary = "내 정보 조회", security = @SecurityRequirement(name = "BearerAuth"))
    @ApiResponse(responseCode = "200", description = "사용자 정보 반환")
    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
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
