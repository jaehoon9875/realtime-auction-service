package com.jaehoon.gateway.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@ConfigurationProperties(prefix = "app.jwt")
@Validated
@Getter
@Setter
public class JwtGatewayProperties {

    // 공개키만 보유 → Gateway는 서명 권한 없이 검증만 수행
    @NotBlank
    private String publicKey;
}
