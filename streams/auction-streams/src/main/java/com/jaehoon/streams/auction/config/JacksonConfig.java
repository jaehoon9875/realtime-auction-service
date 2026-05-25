package com.jaehoon.streams.auction.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Spring Boot 4.x webmvc 단독 구성에서 Jackson ObjectMapper 자동 구성이 없을 때
 * State Store JSON Serde에 사용할 ObjectMapper 빈을 등록한다.
 */
@Configuration
public class JacksonConfig {

    /**
     * State Store 내부 상태(AuctionBidState, AuctionMetadata) JSON 직렬화에 사용한다.
     */
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
