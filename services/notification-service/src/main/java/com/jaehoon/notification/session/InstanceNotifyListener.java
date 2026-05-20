package com.jaehoon.notification.session;

import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer;
import org.springframework.stereotype.Component;

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
        .retry()
        .subscribe();
    log.info("Redis Pub/Sub 구독 시작 channel={}", channel);
  }

  private void dispatchToLocalSession(String payload) {
    try {
      RedisSessionStore.NotifyPayload notify =
          redisSessionStore.readNotifyPayload(payload);
      sessionRegistry
          .sendToLocalSession(notify.sessionId(), notify.message())
          .doOnError(
              error ->
                  log.warn(
                      "로컬 세션 전송 실패 sessionId={}", notify.sessionId(), error))
          .subscribe();
    } catch (JsonProcessingException e) {
      log.warn("Pub/Sub 페이로드 파싱 실패 payload={}", payload, e);
    }
  }
}
