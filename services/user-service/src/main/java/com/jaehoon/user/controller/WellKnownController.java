package com.jaehoon.user.controller;

import com.jaehoon.user.config.JwtProvider;
import io.swagger.v3.oas.annotations.Hidden;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * JWT 서명 검증용 RSA 공개키를 JWKS(RFC 7517) 형식으로 노출한다.
 * auction/bid-service의 OAuth2 Resource Server가 jwk-set-uri로 조회한다.
 */
@Hidden
@RestController
@RequiredArgsConstructor
public class WellKnownController {

    // 키 로테이션 시 JWKS kid와 JWT 헤더 kid를 함께 변경하기 위한 식별자
    private static final String KEY_ID = "user-service-rsa";

    private final JwtProvider jwtProvider;

    @GetMapping("/.well-known/jwks.json")
    public Map<String, Object> jwks() {
        RSAKey rsaKey = new RSAKey.Builder(jwtProvider.getRSAPublicKey())
                .keyID(KEY_ID)
                .algorithm(JWSAlgorithm.RS256)
                .build();
        return new JWKSet(rsaKey).toJSONObject();
    }
}
