package com.jaehoon.user.controller;

import com.jaehoon.user.config.JwtProvider;
import com.jaehoon.user.config.SecurityConfig;
import com.jaehoon.user.config.SecurityProperties;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * JWKS 엔드포인트 HTTP 계약 검증: 공개 접근, 응답 형식(RFC 7517).
 */
@WebMvcTest(WellKnownController.class)
@Import({SecurityConfig.class, WellKnownControllerTest.TestConfig.class})
class WellKnownControllerTest {

    private static KeyPair keyPair;

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    JwtProvider jwtProvider;

    @BeforeAll
    static void generateKeyPair() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        keyPair = gen.generateKeyPair();
    }

    @BeforeEach
    void stubJwtProvider() {
        given(jwtProvider.getRSAPublicKey()).willReturn((RSAPublicKey) keyPair.getPublic());
    }

    @TestConfiguration
    static class TestConfig {

        @Bean
        SecurityProperties securityProperties() {
            return new SecurityProperties(List.of(
                    "/users/signup",
                    "/users/login",
                    "/users/refresh",
                    "/actuator/health",
                    "/.well-known/jwks.json"
            ));
        }

        @Bean
        JwtDecoder jwtDecoder() {
            return NimbusJwtDecoder.withPublicKey((RSAPublicKey) keyPair.getPublic()).build();
        }
    }

    @Test
    @DisplayName("jwks 인증 없이 접근 - 200 OK")
    void jwks_인증없이_200() throws Exception {
        mockMvc.perform(get("/.well-known/jwks.json"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("jwks 응답 - RSA 공개키, kid, alg 포함")
    void jwks_응답형식_RSA키포함() throws Exception {
        mockMvc.perform(get("/.well-known/jwks.json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys").isArray())
                .andExpect(jsonPath("$.keys[0].kty").value("RSA"))
                .andExpect(jsonPath("$.keys[0].kid").value("user-service-rsa"))
                .andExpect(jsonPath("$.keys[0].alg").value("RS256"))
                .andExpect(jsonPath("$.keys[0].n").exists())
                .andExpect(jsonPath("$.keys[0].e").exists());
    }
}
