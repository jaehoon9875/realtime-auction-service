package com.jaehoon.notification.session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 로컬 인스턴스의 WebSocket 세션을 관리하고, Redis 기반 멀티 인스턴스 메시지 라우팅을 수행한다.
 */
@Component
public class WebSocketSessionRegistry {

  private static final Logger log = LoggerFactory.getLogger(WebSocketSessionRegistry.class);

  // sessionId → WebSocketSession (이 인스턴스에 연결된 세션)
  private final Map<String, WebSocketSession> localSessions = new ConcurrentHashMap<>();
  // auctionId → Set<sessionId>
  private final Map<String, Set<String>> auctionSessions = new ConcurrentHashMap<>();
  // userId → sessionId
  private final Map<String, String> userSessions = new ConcurrentHashMap<>();

  private final RedisSessionStore redisSessionStore;

  public WebSocketSessionRegistry(RedisSessionStore redisSessionStore) {
    this.redisSessionStore = redisSessionStore;
  }

  /** 경매 구독 세션 등록 */
  public void registerAuctionSession(String auctionId, WebSocketSession session) {
    String sessionId = session.getId();
    localSessions.put(sessionId, session);
    auctionSessions
        .computeIfAbsent(auctionId, k -> ConcurrentHashMap.newKeySet())
        .add(sessionId);
    redisSessionStore
        .addAuctionSession(auctionId, sessionId)
        .doOnError(
            error ->
                log.error(
                    "Redis 경매 세션 등록 실패 auctionId={} sessionId={}",
                    auctionId,
                    sessionId,
                    error))
        .subscribe();
  }

  /** 개인 알림 세션 등록 */
  public void registerUserSession(String userId, WebSocketSession session) {
    String sessionId = session.getId();
    localSessions.put(sessionId, session);
    userSessions.put(userId, sessionId);
    redisSessionStore
        .setUserSession(userId, sessionId)
        .doOnError(
            error ->
                log.error(
                    "Redis 사용자 세션 등록 실패 userId={} sessionId={}",
                    userId,
                    sessionId,
                    error))
        .subscribe();
  }

  /** 경매 구독 세션 제거 */
  public void removeAuctionSession(String auctionId, String sessionId) {
    localSessions.remove(sessionId);
    Set<String> sessions = auctionSessions.get(auctionId);
    if (sessions != null) {
      sessions.remove(sessionId);
      if (sessions.isEmpty()) {
        auctionSessions.remove(auctionId);
      }
    }
    redisSessionStore
        .removeAuctionSession(auctionId, sessionId)
        .doOnError(
            error ->
                log.error(
                    "Redis 경매 세션 제거 실패 auctionId={} sessionId={}",
                    auctionId,
                    sessionId,
                    error))
        .subscribe();
  }

  /** 개인 알림 세션 제거 */
  public void removeUserSession(String userId, String sessionId) {
    localSessions.remove(sessionId);
    userSessions.remove(userId, sessionId);
    redisSessionStore
        .removeUserSession(userId)
        .doOnError(
            error ->
                log.error(
                    "Redis 사용자 세션 제거 실패 userId={} sessionId={}",
                    userId,
                    sessionId,
                    error))
        .subscribe();
  }

  /**
   * 특정 경매의 모든 구독자에게 메시지 전송.
   *
   * @param auctionId 대상 경매 ID
   * @param message 전송할 JSON 문자열
   */
  public Mono<Void> sendToAuction(String auctionId, String message) {
    return redisSessionStore
        .getAuctionSessionTargets(auctionId)
        .flatMap(target -> routeMessage(target, message))
        .then();
  }

  /**
   * 특정 사용자에게 메시지 전송.
   *
   * @param userId 대상 사용자 ID
   * @param message 전송할 JSON 문자열
   */
  public Mono<Void> sendToUser(String userId, String message) {
    return redisSessionStore
        .getUserSessionTarget(userId)
        .flatMap(target -> routeMessage(target, message))
        .then();
  }

  /**
   * 이 인스턴스에 연결된 로컬 WebSocket 세션에만 메시지를 전송한다.
   * Redis Pub/Sub 수신(InstanceNotifyListener) 경로에서 사용한다.
   *
   * @param sessionId WebSocket 세션 ID
   * @param message 전송할 JSON 문자열
   */
  public Mono<Void> sendToLocalSession(String sessionId, String message) {
    WebSocketSession session = localSessions.get(sessionId);
    if (session == null) {
      return Mono.empty();
    }
    return session.send(Mono.just(session.textMessage(message)));
  }

  private Mono<Void> routeMessage(RedisSessionStore.SessionTarget target, String message) {
    if (redisSessionStore.getInstanceId().equals(target.instanceId())) {
      return sendToLocalSession(target.sessionId(), message);
    }
    return redisSessionStore.publishNotify(target.instanceId(), target.sessionId(), message);
  }
}
