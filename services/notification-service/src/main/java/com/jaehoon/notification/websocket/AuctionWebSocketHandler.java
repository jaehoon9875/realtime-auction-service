package com.jaehoon.notification.websocket;

import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.util.UriTemplate;

import com.jaehoon.notification.session.WebSocketSessionRegistry;

import reactor.core.publisher.Mono;

/**
 * /ws/auctions/{auctionId} 연결 처리.
 * 경매 구독자 전체에게 BID_UPDATED, AUCTION_CLOSED 이벤트를 브로드캐스트한다.
 */
@Component
public class AuctionWebSocketHandler implements WebSocketHandler {

    private static final UriTemplate AUCTION_URI_TEMPLATE = new UriTemplate("/ws/auctions/{auctionId}");

    private final WebSocketSessionRegistry registry;

    public AuctionWebSocketHandler(WebSocketSessionRegistry registry) {
        this.registry = registry;
    }

    /**
     * WebSocket 연결 수립 시 세션을 등록하고, 연결 해제 시 세션을 정리한다.
     */
    @Override
    public Mono<Void> handle(WebSocketSession session) {
        // 경매 ID 추출 및 세션 등록
        String auctionId = extractAuctionId(session.getHandshakeInfo().getUri().getPath());
        registry.registerAuctionSession(auctionId, session);

        // receive()를 소비해야 연결이 유지된다. Flux 종료 시점이 클라이언트 disconnect 감지 시점이다.
        return session.receive()
                .doFinally(signal -> registry.removeAuctionSession(auctionId, session.getId()))
                .then();
    }

    /**
     * UriTemplate을 사용하여 경매 ID를 추출한다.
     * 
     * @param path
     * @return
     */
    private String extractAuctionId(String path) {
        return AUCTION_URI_TEMPLATE.match(path).get("auctionId");
    }
}
