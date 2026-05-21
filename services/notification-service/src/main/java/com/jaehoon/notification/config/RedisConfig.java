package com.jaehoon.notification.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer;

/**
 * Notification Service용 Reactive Redis Pub/Sub 리스너 컨테이너를 등록한다.
 * ReactiveStringRedisTemplate은 spring-boot-starter-data-redis-reactive 자동 구성을 사용한다.
 */
@Configuration
public class RedisConfig {

  @Bean
  public ReactiveRedisMessageListenerContainer reactiveRedisMessageListenerContainer(
      ReactiveRedisConnectionFactory connectionFactory) {
    return new ReactiveRedisMessageListenerContainer(connectionFactory);
  }
}
