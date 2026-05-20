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

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        // SecurityWebFilterChain이 핸드셰이크 시 ?token= JWT를 검증하고 Principal을 세팅함
        return session.getHandshakeInfo().getPrincipal()
                .flatMap(principal -> {
                    String userId = principal.getName();
                    registry.registerUserSession(userId, session);

                    return session.receive()
                            .doFinally(signal -> registry.removeUserSession(userId, session.getId()))
                            .then();
                });
    }
}
