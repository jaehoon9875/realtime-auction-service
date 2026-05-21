package com.jaehoon.notification.session;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer;
import org.springframework.stereotype.Component;
import reactor.util.retry.Retry;

/**
 * 이 인스턴스 전용 Redis Pub/Sub 채널(notify:{instanceId})을 구독하고,
 * 수신한 메시지를 로컬 WebSocket 세션으로 전달한다.
 */
@Component
public class InstanceNotifyListener {

  private static final Logger log = LoggerFactory.getLogger(InstanceNotifyListener.class);

  private final ReactiveRedisMessageListenerContainer listenerContainer;
  private final RedisSessionStore redisSessionStore;
  private final WebSocketSessionRegistry sessionRegistry;
  private final String instanceId;

  public InstanceNotifyListener(
      ReactiveRedisMessageListenerContainer listenerContainer,
      RedisSessionStore redisSessionStore,
      WebSocketSessionRegistry sessionRegistry,
      @Value("${app.instance-id}") String instanceId) {
    this.listenerContainer = listenerContainer;
    this.redisSessionStore = redisSessionStore;
    this.sessionRegistry = sessionRegistry;
    this.instanceId = instanceId;
  }

  /** 애플리케이션 기동 시 notify:{instanceId} 채널 구독을 시작한다. */
  @PostConstruct
  public void subscribe() {
    String channel = redisSessionStore.notifyChannelForInstance(instanceId);
    listenerContainer
        .receive(new ChannelTopic(channel))
        .map(message -> message.getMessage())
        .doOnNext(this::dispatchToLocalSession)
        .doOnError(error -> log.error("Redis Pub/Sub 수신 오류 channel={}", channel, error))
        // Redis 장애 시 서버 회복 시간을 확보하기 위해 지수 백오프로 재구독한다.
        .retryWhen(Retry.backoff(Long.MAX_VALUE, Duration.ofSeconds(1)).maxBackoff(Duration.ofSeconds(30)))
        .subscribe();
    log.info("Redis Pub/Sub 구독 시작 channel={}", channel);
  }

  private void dispatchToLocalSession(String payload) {
    redisSessionStore.readNotifyPayload(payload)
        .flatMap(notify -> sessionRegistry.sendToLocalSession(notify.sessionId(), notify.message())
            .doOnError(error -> log.warn("로컬 세션 전송 실패 sessionId={}", notify.sessionId(), error)))
        .doOnError(error -> log.warn("Pub/Sub 페이로드 파싱 실패 payload={}", payload, error))
        .subscribe();
  }
}
