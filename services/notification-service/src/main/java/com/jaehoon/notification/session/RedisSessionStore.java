package com.jaehoon.notification.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * WebSocket 세션 위치를 Redis에 저장하고, 다른 인스턴스로 메시지를 라우팅한다.
 */
@Slf4j
@Component
public class RedisSessionStore {

  private static final String AUCTION_SESSIONS_KEY = "auction:%s:sessions";
  private static final String USER_SESSION_KEY = "ws:user:%s:session";
  // 현재 값이 기대값과 같을 때만 키를 삭제하는 원자적 compare-and-delete
  private static final RedisScript<Long> DELETE_IF_MATCH_SCRIPT = RedisScript.of(
      "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
      Long.class);
  private static final String NOTIFY_CHANNEL = "notify:%s";
  // 서버 비정상 종료 시 ghost session이 Redis에 영구 잔류하는 것을 방지하는 안전망 TTL
  private static final Duration SESSION_TTL = Duration.ofHours(24);

  private final ReactiveStringRedisTemplate redis;
  private final ObjectMapper objectMapper;
  private final String instanceId;

  public RedisSessionStore(
      ReactiveStringRedisTemplate redis,
      ObjectMapper objectMapper,
      @Value("${app.instance-id}") String instanceId) {
    this.redis = redis;
    this.objectMapper = objectMapper;
    this.instanceId = instanceId;
  }

  /**
   * 이 JVM 인스턴스 식별자를 반환한다.
   *
   * @return 이 JVM 인스턴스 식별자
   */
  public String getInstanceId() {
    return instanceId;
  }

  /**
   * 경매 구독 세션을 Redis Set에 등록한다.
   *
   * @param auctionId 경매 ID
   * @param sessionId WebSocket 세션 ID
   */
  public Mono<Void> addAuctionSession(String auctionId, String sessionId) {
    String key = auctionSessionsKey(auctionId);
    return redis.opsForSet()
        .add(key, sessionRef(sessionId))
        .then(redis.expire(key, SESSION_TTL))
        .then();
  }

  /**
   * 경매 구독 세션을 Redis Set에서 제거한다.
   *
   * @param auctionId 경매 ID
   * @param sessionId WebSocket 세션 ID
   */
  public Mono<Void> removeAuctionSession(String auctionId, String sessionId) {
    return redis.opsForSet()
        .remove(auctionSessionsKey(auctionId), sessionRef(sessionId))
        .then();
  }

  /**
   * 사용자 개인 알림 세션을 Redis에 등록한다.
   *
   * @param userId    사용자 ID
   * @param sessionId WebSocket 세션 ID
   */
  public Mono<Void> setUserSession(String userId, String sessionId) {
    return redis.opsForValue()
        .set(userSessionKey(userId), sessionRef(sessionId), SESSION_TTL)
        .then();
  }

  /**
   * 사용자 개인 알림 세션을 Redis에서 제거한다.
   *
   * @param userId 사용자 ID
   */
  public Mono<Void> removeUserSession(String userId) {
    return redis.delete(userSessionKey(userId)).then();
  }

  /**
   * Redis에 저장된 세션이 지정한 sessionId와 일치할 때만 원자적으로 삭제한다.
   * 재연결 레이스 컨디션에서 최신 세션을 덮어쓰는 것을 방지한다.
   *
   * @param userId    사용자 ID
   * @param sessionId 삭제 대상 세션 ID (현재 저장값과 다르면 삭제하지 않음)
   */
  public Mono<Void> removeUserSessionIfMatch(String userId, String sessionId) {
    return redis.execute(DELETE_IF_MATCH_SCRIPT, List.of(userSessionKey(userId)), sessionRef(sessionId))
        .then();
  }

  /**
   * 경매 구독자 전체의 세션 위치(instanceId, sessionId)를 조회한다.
   *
   * @param auctionId 경매 ID
   */
  public Flux<SessionTarget> getAuctionSessionTargets(String auctionId) {
    return redis.opsForSet()
        .members(auctionSessionsKey(auctionId))
        .flatMap(ref -> {
          try {
            return Mono.just(parseSessionRef(ref));
          } catch (IllegalArgumentException e) {
            // 잘못된 ref 하나로 전체 구독자 알림이 누락되지 않도록 해당 항목만 건너뛴다.
            log.warn("잘못된 세션 ref 건너뜀. auctionId={}, ref={}", auctionId, ref);
            return Mono.empty();
          }
        });
  }

  /**
   * 사용자 개인 알림 세션 위치를 조회한다.
   *
   * @param userId 사용자 ID
   */
  public Mono<SessionTarget> getUserSessionTarget(String userId) {
    return redis.opsForValue()
        .get(userSessionKey(userId))
        .map(this::parseSessionRef);
  }

  /**
   * 대상 인스턴스의 Pub/Sub 채널로 WebSocket 메시지를 발행한다.
   *
   * @param targetInstanceId 메시지를 받을 인스턴스 ID
   * @param sessionId        대상 WebSocket 세션 ID
   * @param message          전송할 JSON 문자열
   */
  public Mono<Void> publishNotify(String targetInstanceId, String sessionId, String message) {
    String channel = NOTIFY_CHANNEL.formatted(targetInstanceId);
    return Mono.fromCallable(() -> objectMapper.writeValueAsString(new NotifyPayload(sessionId, message)))
        .flatMap(payload -> redis.convertAndSend(channel, payload))
        .then();
  }

  /** Pub/Sub 채널명: notify:{instanceId} */
  public String notifyChannelForInstance(String instanceId) {
    return NOTIFY_CHANNEL.formatted(instanceId);
  }

  /** NotifyPayload JSON 역직렬화 */
  public Mono<NotifyPayload> readNotifyPayload(String json) {
    return Mono.fromCallable(() -> objectMapper.readValue(json, NotifyPayload.class));
  }

  private String sessionRef(String sessionId) {
    return instanceId + "|" + sessionId;
  }

  private SessionTarget parseSessionRef(String ref) {
    int pipe = ref.indexOf('|');
    if (pipe <= 0 || pipe >= ref.length() - 1) {
      throw new IllegalArgumentException("Invalid session ref: " + ref);
    }
    return new SessionTarget(ref.substring(0, pipe), ref.substring(pipe + 1));
  }

  private static String auctionSessionsKey(String auctionId) {
    return AUCTION_SESSIONS_KEY.formatted(auctionId);
  }

  private static String userSessionKey(String userId) {
    return USER_SESSION_KEY.formatted(userId);
  }

  /** Redis에 저장된 세션 위치 (instanceId + sessionId) */
  public record SessionTarget(String instanceId, String sessionId) {
  }

  /** Pub/Sub 채널 notify:{instanceId} 페이로드 */
  public record NotifyPayload(String sessionId, String message) {
  }
}
