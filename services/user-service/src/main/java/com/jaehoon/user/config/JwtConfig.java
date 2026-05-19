package com.jaehoon.user.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * JWT 설정 프로퍼티 바인딩을 활성화한다.
 */
@Configuration
@EnableConfigurationProperties(JwtProvider.class)
public class JwtConfig {
}
