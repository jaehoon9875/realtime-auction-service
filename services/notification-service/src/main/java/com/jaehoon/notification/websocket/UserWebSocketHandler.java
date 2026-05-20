package com.jaehoon.notification.websocket;

import com.jaehoon.notification.session.WebSocketSessionRegistry;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Mono;

/**
 * /ws/users/me 연결 처리.
 * AUCTION_WON, OUTBID, BID_REJECTED 등 개인 알림을 전달한다.
 */
@Component
public class UserWebSocketHandler implements WebSocketHandler {

    private final WebSocketSessionRegistry registry;

    public UserWebSocketHandler(WebSocketSessionRegistry registry) {
        this.registry = registry;
    }

    /**
     * WebSocket 연결 수립 시 세션을 등록하고, 연결 해제 시 세션을 정리한다.
     * receive()를 소비해야 연결이 유지된다. Flux 종료 시점이 클라이언트 disconnect 감지 시점이다.
     */
    @Override
    public Mono<Void> handle(WebSocketSession session) {
        // SecurityWebFilterChain이 핸드셰이크 시 ?token= JWT를 검증하고 Principal을 세팅함
        return session.getHandshakeInfo().getPrincipal()
                .switchIfEmpty(session.close().then(Mono.empty()))  // 안전망: Principal 없으면 즉시 종료
                .flatMap(principal -> {
                    String userId = principal.getName();
                    registry.registerUserSession(userId, session);

                    return session.receive()
                            .doFinally(signal -> registry.removeUserSession(userId, session.getId()))
                            .then();
                });
    }
}
