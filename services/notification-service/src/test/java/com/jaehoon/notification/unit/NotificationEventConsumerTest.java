package com.jaehoon.notification.unit;

import static com.jaehoon.notification.kafka.NotificationTypes.AUCTION_CLOSED;
import static com.jaehoon.notification.kafka.NotificationTypes.BID_REJECTED;
import static com.jaehoon.notification.kafka.NotificationTypes.BID_UPDATED;
import static com.jaehoon.notification.kafka.NotificationTypes.OUTBID;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jaehoon.auction.events.NotificationEvent;
import com.jaehoon.notification.kafka.NotificationEventConsumer;
import com.jaehoon.notification.kafka.NotificationMessageMapper;
import com.jaehoon.notification.session.WebSocketSessionRegistry;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

/**
 * notification-events 소비 시 이벤트 타입별 WebSocket 라우팅 검증.
 */
@ExtendWith(MockitoExtension.class)
class NotificationEventConsumerTest {

    @Mock
    private WebSocketSessionRegistry sessionRegistry;

    @Mock
    private NotificationMessageMapper messageMapper;

    private NotificationEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new NotificationEventConsumer(sessionRegistry, messageMapper);
    }

    @Test
    void BID_UPDATED는_경매방_브로드캐스트로_라우팅한다() {
        NotificationEvent event = NotificationEvent.newBuilder()
                .setEventId("e1")
                .setNotificationType(BID_UPDATED)
                .setTargetAuctionId("auction-1")
                .setAuctionId("auction-1")
                .setPayload(Map.of("currentPrice", "1000", "bidCount", "1"))
                .setOccurredAt(1_736_947_200L)
                .build();
        when(messageMapper.toWebSocketMessage(event)).thenReturn("{\"type\":\"" + BID_UPDATED + "\"}");
        when(sessionRegistry.sendToAuction(eq("auction-1"), anyString()))
                .thenReturn(Mono.empty());

        consumer.consume(event);

        verify(sessionRegistry).sendToAuction("auction-1", "{\"type\":\"" + BID_UPDATED + "\"}");
        verify(sessionRegistry, never()).sendToUser(anyString(), anyString());
    }

    @Test
    void AUCTION_CLOSED는_targetAuctionId_우선으로_브로드캐스트한다() {
        NotificationEvent event = NotificationEvent.newBuilder()
                .setEventId("e2")
                .setNotificationType(AUCTION_CLOSED)
                .setTargetAuctionId("target-auction")
                .setAuctionId("auction-fallback")
                .setPayload(Map.of())
                .setOccurredAt(1_736_947_200L)
                .build();
        when(messageMapper.toWebSocketMessage(event)).thenReturn("{\"type\":\"" + AUCTION_CLOSED + "\"}");
        when(sessionRegistry.sendToAuction(eq("target-auction"), anyString()))
                .thenReturn(Mono.empty());

        consumer.consume(event);

        verify(sessionRegistry).sendToAuction("target-auction", "{\"type\":\"" + AUCTION_CLOSED + "\"}");
    }

    @Test
    void BID_REJECTED는_개인_알림으로_라우팅한다() {
        NotificationEvent event = NotificationEvent.newBuilder()
                .setEventId("e3")
                .setNotificationType(BID_REJECTED)
                .setTargetUserId("user-bidder")
                .setAuctionId("auction-1")
                .setPayload(Map.of("rejectedPrice", "500", "reason", "PRICE_TOO_LOW"))
                .setOccurredAt(1_736_947_200L)
                .build();
        when(messageMapper.toWebSocketMessage(event)).thenReturn("{\"type\":\"" + BID_REJECTED + "\"}");
        when(sessionRegistry.sendToUser(eq("user-bidder"), anyString())).thenReturn(Mono.empty());

        consumer.consume(event);

        verify(sessionRegistry).sendToUser("user-bidder", "{\"type\":\"" + BID_REJECTED + "\"}");
        verify(sessionRegistry, never()).sendToAuction(anyString(), anyString());
    }

    @Test
    void targetUserId가_없으면_개인_알림을_보내지_않는다() {
        NotificationEvent event = NotificationEvent.newBuilder()
                .setEventId("e4")
                .setNotificationType(OUTBID)
                .setAuctionId("auction-1")
                .setPayload(Map.of("newHighestBid", "1000"))
                .setOccurredAt(1_736_947_200L)
                .build();

        consumer.consume(event);

        verify(sessionRegistry, never()).sendToUser(anyString(), anyString());
        verify(messageMapper, never()).toWebSocketMessage(event);
    }

    @Test
    void 알_수_없는_타입은_무시한다() {
        NotificationEvent event = NotificationEvent.newBuilder()
                .setEventId("e5")
                .setNotificationType("UNKNOWN_TYPE")
                .setAuctionId("auction-1")
                .setPayload(Map.of())
                .setOccurredAt(1_736_947_200L)
                .build();

        consumer.consume(event);

        verify(sessionRegistry, never()).sendToAuction(anyString(), anyString());
        verify(sessionRegistry, never()).sendToUser(anyString(), anyString());
    }
}
