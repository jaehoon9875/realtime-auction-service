package com.jaehoon.auction.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Spring Boot 4.x webmvc 단독 구성에서 Jackson ObjectMapper 자동 구성이 없을 때
 * SecurityConfig 등에서 사용할 ObjectMapper 빈을 등록한다.
 */
@Configuration
public class JacksonConfig {

    /**
     * 인증 실패 응답 JSON 직렬화 등에 사용한다.
     */
    @Bean
    ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
