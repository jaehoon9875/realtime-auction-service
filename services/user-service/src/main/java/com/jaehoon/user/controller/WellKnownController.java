package com.jaehoon.user.controller;

import com.jaehoon.user.config.JwtProvider;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * RFC 8414 준수 JWKS 엔드포인트를 제공한다.
 * API Gateway 및 각 서비스가 JWT 서명 검증에 사용하는 RSA 공개키를 반환한다.
 */
@RestController
@RequiredArgsConstructor
public class WellKnownController {

    private final JwtProvider jwtProvider;

    @GetMapping("/.well-known/jwks.json")
    public Map<String, Object> jwks() {
        RSAKey rsaKey = new RSAKey.Builder(jwtProvider.getRSAPublicKey())
                .keyID("user-service-rsa")
                .build();
        return new JWKSet(rsaKey).toJSONObject();
    }
}
