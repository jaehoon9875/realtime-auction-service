package com.jaehoon.notification.config;

import com.jaehoon.notification.websocket.AuctionWebSocketHandler;
import com.jaehoon.notification.websocket.UserWebSocketHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * WebSocket URL → 핸들러 매핑과 업그레이드 처리 어댑터를 등록하는 설정.
 */
@Configuration
public class WebSocketConfig {

    private static final String WS_AUCTION = "/ws/auctions/*";
    private static final String WS_USER_ME = "/ws/users/me";
    private static final int WS_HANDLER_ORDER = -1;

    /**
     * WebSocket URL → 핸들러 매핑을 등록한다.
     */
    @Bean
    public HandlerMapping webSocketHandlerMapping(
            AuctionWebSocketHandler auctionHandler,
            UserWebSocketHandler userHandler) {
        Map<String, WebSocketHandler> urlMap = new LinkedHashMap<>();
        urlMap.put(WS_AUCTION, auctionHandler);
        urlMap.put(WS_USER_ME, userHandler);
        // order=-1 로 @RequestMapping보다 먼저 WebSocket 업그레이드 요청을 처리한다.
        return new SimpleUrlHandlerMapping(urlMap, WS_HANDLER_ORDER);
    }

    /**
     * WebSocketHandler를 호출하는 어댑터 빈.
     * WebFlux가 HandlerMapping과 조합하여 사용한다.
     */
    @Bean
    public WebSocketHandlerAdapter webSocketHandlerAdapter() {
        return new WebSocketHandlerAdapter();
    }
}
