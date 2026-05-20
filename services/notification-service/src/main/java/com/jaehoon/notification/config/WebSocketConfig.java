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

@Configuration
public class WebSocketConfig {

    /**
     * WebSocket URL → 핸들러 매핑.
     * order=-1: @RequestMapping보다 높은 우선순위로 WebSocket 업그레이드 요청을 먼저 처리한다.
     */
    @Bean
    public HandlerMapping webSocketHandlerMapping(
            AuctionWebSocketHandler auctionHandler,
            UserWebSocketHandler userHandler) {
        Map<String, WebSocketHandler> urlMap = new LinkedHashMap<>();
        urlMap.put("/ws/auctions/*", auctionHandler);
        urlMap.put("/ws/users/me", userHandler);
        return new SimpleUrlHandlerMapping(urlMap, -1);
    }

    /** WebSocket 업그레이드 요청을 WebSocketHandler로 위임하는 어댑터. */
    @Bean
    public WebSocketHandlerAdapter webSocketHandlerAdapter() {
        return new WebSocketHandlerAdapter();
    }
}
