package com.jaehoon.gateway.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * application.yml의 app.jwt 프리픽스로부터 JWT 검증용 공개키 설정을 바인딩한다.
 */
@ConfigurationProperties(prefix = "app.jwt")
@Validated
public class JwtGatewayProperties {

    // 공개키만 보유 → Gateway는 서명 권한 없이 검증만 수행
    @NotBlank
    private final String publicKey;

    /**
     * JwtGatewayProperties 생성자
     * 
     * @param publicKey JWT 공개키
     */
    public JwtGatewayProperties(String publicKey) {
        this.publicKey = publicKey;
    }

    /**
     * JWT 공개키를 반환한다.
     * 
     * @return JWT 공개키
     */
    public String getPublicKey() {
        return publicKey;
    }
}
