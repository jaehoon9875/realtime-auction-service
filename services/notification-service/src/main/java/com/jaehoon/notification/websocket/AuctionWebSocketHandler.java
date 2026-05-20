package com.jaehoon.notification.websocket;

import com.jaehoon.notification.session.WebSocketSessionRegistry;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Mono;

/**
 * /ws/auctions/{auctionId} 연결 처리.
 * 경매 구독자 전체에게 BID_UPDATED, AUCTION_CLOSED 이벤트를 브로드캐스트한다.
 */
@Component
public class AuctionWebSocketHandler implements WebSocketHandler {

    private final WebSocketSessionRegistry registry;

    public AuctionWebSocketHandler(WebSocketSessionRegistry registry) {
        this.registry = registry;
    }

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        String auctionId = extractAuctionId(session.getHandshakeInfo().getUri().getPath());
        registry.registerAuctionSession(auctionId, session);

        // receive()를 소비해야 세션이 살아있음. 클라이언트 disconnect 시 Flux가 종료되어 세션을 닫는다.
        return session.receive()
                .doFinally(signal -> registry.removeAuctionSession(auctionId, session.getId()))
                .then();
    }

    /** /ws/auctions/abc123 → abc123 */
    private String extractAuctionId(String path) {
        return path.substring(path.lastIndexOf('/') + 1);
    }
}
