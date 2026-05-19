package com.jaehoon.user.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * JWT 설정 프로퍼티 바인딩 및 JwtDecoder 빈을 등록한다.
 */
@Configuration
@EnableConfigurationProperties(JwtProvider.class)
public class JwtConfig {

    /**
     * RSA 공개키로 JWT 서명을 검증하는 디코더를 등록한다.
     * user-service는 토큰 발급자이므로 JWKS 엔드포인트 대신 공개키를 직접 사용한다.
     */
    @Bean
    JwtDecoder jwtDecoder(JwtProvider jwtProvider) {
        return NimbusJwtDecoder.withPublicKey(jwtProvider.getRSAPublicKey()).build();
    }
}
