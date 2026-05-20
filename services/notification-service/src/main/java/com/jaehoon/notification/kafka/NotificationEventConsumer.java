package com.jaehoon.notification.kafka;

import com.jaehoon.auction.events.NotificationEvent;
import com.jaehoon.notification.session.WebSocketSessionRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * notification-events 토픽을 소비해 WebSocket 세션으로 알림을 전달한다.
 */
@Component
public class NotificationEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventConsumer.class);

    private final WebSocketSessionRegistry sessionRegistry;
    private final NotificationMessageMapper messageMapper;

    public NotificationEventConsumer(
            WebSocketSessionRegistry sessionRegistry,
            NotificationMessageMapper messageMapper) {
        this.sessionRegistry = sessionRegistry;
        this.messageMapper = messageMapper;
    }

    @KafkaListener(topics = "notification-events", groupId = "notification-service")
    public void consume(NotificationEvent event) {
        String notificationType = event.getNotificationType().toString();
        switch (notificationType) {
            case "BID_UPDATED" -> handleBidUpdated(event);
            case "AUCTION_CLOSED" -> handleAuctionClosed(event);
            case "AUCTION_WON", "OUTBID", "BID_REJECTED" -> handlePersonal(event);
            default -> log.warn("알 수 없는 notificationType, 건너뜀. type={}, eventId={}",
                    notificationType, event.getEventId());
        }
    }

    private void handleBidUpdated(NotificationEvent event) {
        String auctionId = resolveAuctionId(event);
        if (auctionId == null) {
            log.warn("BID_UPDATED 라우팅 실패: auctionId 없음. eventId={}", event.getEventId());
            return;
        }
        pushToAuction(auctionId, event);
    }

    private void handleAuctionClosed(NotificationEvent event) {
        String auctionId = resolveAuctionId(event);
        if (auctionId == null) {
            log.warn("AUCTION_CLOSED 라우팅 실패: auctionId 없음. eventId={}", event.getEventId());
            return;
        }
        pushToAuction(auctionId, event);
    }

    private void handlePersonal(NotificationEvent event) {
        if (event.getTargetUserId() == null) {
            log.warn("개인 알림 라우팅 실패: targetUserId 없음. type={}, eventId={}",
                    event.getNotificationType(), event.getEventId());
            return;
        }
        String userId = event.getTargetUserId().toString();
        String message;
        try {
            message = messageMapper.toWebSocketMessage(event);
        } catch (IllegalArgumentException e) {
            log.warn("개인 알림 매핑 실패. type={}, eventId={}", event.getNotificationType(), event.getEventId(), e);
            return;
        }
        sessionRegistry.sendToUser(userId, message)
                .doOnError(e -> log.error("WebSocket 전송 실패. userId={}, type={}", userId, event.getNotificationType(), e))
                .subscribe();
    }

    private void pushToAuction(String auctionId, NotificationEvent event) {
        String message;
        try {
            message = messageMapper.toWebSocketMessage(event);
        } catch (IllegalArgumentException e) {
            log.warn("경매 알림 매핑 실패. type={}, eventId={}", event.getNotificationType(), event.getEventId(), e);
            return;
        }
        sessionRegistry.sendToAuction(auctionId, message)
                .doOnError(e -> log.error("WebSocket 전송 실패. auctionId={}, type={}", auctionId, event.getNotificationType(), e))
                .subscribe();
    }

    /** 브로드캐스트는 targetAuctionId 우선, 없으면 auctionId */
    private String resolveAuctionId(NotificationEvent event) {
        if (event.getTargetAuctionId() != null) {
            return event.getTargetAuctionId().toString();
        }
        if (event.getAuctionId() != null) {
            return event.getAuctionId().toString();
        }
        return null;
    }
}
