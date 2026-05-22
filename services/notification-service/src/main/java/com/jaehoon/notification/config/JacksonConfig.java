package com.jaehoon.notification.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * WebFlux 단독 구성에서 Jackson ObjectMapper 자동 구성이 없을 때 사용할 ObjectMapper 빈을 등록한다.
 */
@Configuration
public class JacksonConfig {

  /**
   * Redis Pub/Sub 페이로드·WebSocket 메시지 JSON 직렬화에 사용할 ObjectMapper를 제공한다.
   */
  @Bean
  public ObjectMapper objectMapper() {
    return new ObjectMapper();
  }
}
