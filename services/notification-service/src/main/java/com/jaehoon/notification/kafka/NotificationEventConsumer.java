package com.jaehoon.notification.kafka;

import static com.jaehoon.notification.kafka.NotificationTypes.AUCTION_CLOSED;
import static com.jaehoon.notification.kafka.NotificationTypes.AUCTION_WON;
import static com.jaehoon.notification.kafka.NotificationTypes.BID_REJECTED;
import static com.jaehoon.notification.kafka.NotificationTypes.BID_UPDATED;
import static com.jaehoon.notification.kafka.NotificationTypes.OUTBID;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.jaehoon.auction.events.NotificationEvent;
import com.jaehoon.notification.session.WebSocketSessionRegistry;

import lombok.extern.slf4j.Slf4j;

/**
 * notification-events 토픽을 소비해 WebSocket 세션으로 알림을 전달한다.
 * notificationType에 따라 경매 구독자 브로드캐스트(/ws/auctions)와 개인 알림(/ws/users/me)으로
 * 라우팅한다.
 */
@Slf4j
@Component
public class NotificationEventConsumer {

    private final WebSocketSessionRegistry sessionRegistry;
    private final NotificationMessageMapper messageMapper;

    public NotificationEventConsumer(
            WebSocketSessionRegistry sessionRegistry,
            NotificationMessageMapper messageMapper) {
        this.sessionRegistry = sessionRegistry;
        this.messageMapper = messageMapper;
    }

    /**
     * Kafka notification-events 이벤트를 수신해 타입별 WebSocket 라우팅을 수행한다.
     *
     * @param event NotificationEvent (Avro)
     */
    @KafkaListener(topics = "${app.kafka.topics.notification-events}")
    public void consume(NotificationEvent event) {
        String notificationType = event.getNotificationType().toString();
        switch (notificationType) {
            case BID_UPDATED -> handleBidUpdated(event);
            case AUCTION_CLOSED -> handleAuctionClosed(event);
            case AUCTION_WON, OUTBID, BID_REJECTED -> handlePersonal(event);
            default -> log.warn("알 수 없는 notificationType, 건너뜀. type={}, eventId={}",
                    notificationType, event.getEventId());
        }
    }

    // BID_UPDATED는 /ws/auctions/{auctionId} 구독자 전체에 브로드캐스트
    private void handleBidUpdated(NotificationEvent event) {
        String auctionId = resolveAuctionId(event);
        if (auctionId == null) {
            log.warn("BID_UPDATED 라우팅 실패: auctionId 없음. eventId={}", event.getEventId());
            return;
        }
        pushToAuction(auctionId, event);
    }

    // AUCTION_CLOSED는 경매 마감 알림용 브로드캐스트 — Auction DB status와 별개(알림 파이프라인)
    private void handleAuctionClosed(NotificationEvent event) {
        String auctionId = resolveAuctionId(event);
        if (auctionId == null) {
            log.warn("AUCTION_CLOSED 라우팅 실패: auctionId 없음. eventId={}", event.getEventId());
            return;
        }
        pushToAuction(auctionId, event);
    }

    // AUCTION_WON·OUTBID·BID_REJECTED는 targetUserId 기준 개인 알림
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
        } catch (NotificationMappingException e) {
            // 지원하지 않는 타입이거나 직렬화 실패 — 해당 이벤트만 스킵하고 Kafka 리스너는 유지한다.
            log.warn("개인 알림 매핑 실패. type={}, eventId={}", event.getNotificationType(), event.getEventId(), e);
            return;
        }
        // WebFlux Mono — Kafka listener 스레드에서 fire-and-forget 전송
        sessionRegistry.sendToUser(userId, message)
                .subscribe(
                        null,
                        e -> log.error("WebSocket 전송 실패. userId={}, type={}", userId, event.getNotificationType(), e));
    }

    private void pushToAuction(String auctionId, NotificationEvent event) {
        String message;
        try {
            message = messageMapper.toWebSocketMessage(event);
        } catch (NotificationMappingException e) {
            // 지원하지 않는 타입이거나 직렬화 실패 — 해당 이벤트만 스킵하고 Kafka 리스너는 유지한다.
            log.warn("경매 알림 매핑 실패. type={}, eventId={}", event.getNotificationType(), event.getEventId(), e);
            return;
        }
        sessionRegistry.sendToAuction(auctionId, message)
                .subscribe(
                        null,
                        e -> log.error("WebSocket 전송 실패. auctionId={}, type={}", auctionId, event.getNotificationType(), e));
    }

    /**
     * 브로드캐스트 대상 경매 ID를 결정한다.
     * Streams 발행 시 targetAuctionId를 우선 사용하고, 없으면 auctionId로 fallback한다.
     */
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
