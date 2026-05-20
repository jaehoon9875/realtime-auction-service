package com.jaehoon.notification.kafka;

import static com.jaehoon.notification.kafka.NotificationTypes.AUCTION_CLOSED;
import static com.jaehoon.notification.kafka.NotificationTypes.AUCTION_WON;
import static com.jaehoon.notification.kafka.NotificationTypes.BID_REJECTED;
import static com.jaehoon.notification.kafka.NotificationTypes.BID_UPDATED;
import static com.jaehoon.notification.kafka.NotificationTypes.OUTBID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jaehoon.auction.events.NotificationEvent;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * NotificationEvent를 docs/api.md WebSocket 메시지 JSON으로 변환한다.
 */
@Component
public class NotificationMessageMapper {

    private static final DateTimeFormatter ISO_LOCAL =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss").withZone(ZoneOffset.UTC);

    private final ObjectMapper objectMapper;

    public NotificationMessageMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * NotificationEvent를 WebSocket push용 JSON 문자열로 변환한다.
     *
     * @param event NotificationEvent (Avro)
     * @return docs/api.md 형식의 JSON 문자열
     * @throws IllegalArgumentException 지원하지 않는 notificationType
     */
    public String toWebSocketMessage(NotificationEvent event) {
        String type = event.getNotificationType().toString();
        return switch (type) {
            case BID_UPDATED -> toJson(buildBidUpdated(event));
            case AUCTION_CLOSED -> toJson(buildAuctionClosed(event));
            case AUCTION_WON -> toJson(buildAuctionWon(event));
            case OUTBID -> toJson(buildOutbid(event));
            case BID_REJECTED -> toJson(buildBidRejected(event));
            default -> throw new IllegalArgumentException("지원하지 않는 notificationType: " + type);
        };
    }

    private ObjectNode buildBidUpdated(NotificationEvent event) {
        Map<String, String> payload = event.getPayload();
        ObjectNode node = baseNode(BID_UPDATED, event);
        putLongField(node, "currentPrice", payload, "currentPrice");
        putLongField(node, "bidCount", payload, "bidCount");
        return node;
    }

    private ObjectNode buildAuctionClosed(NotificationEvent event) {
        Map<String, String> payload = event.getPayload();
        ObjectNode node = baseNode(AUCTION_CLOSED, event);
        // Streams는 title만 넣을 수 있어 finalPrice·winnerId는 payload 키 fallback
        if (!putLongField(node, "finalPrice", payload, "finalPrice", "highestBid")) {
            node.putNull("finalPrice");
        }
        if (!putTextField(node, "winnerId", payload, "winnerId", "highestBidderId")) {
            node.putNull("winnerId");
        }
        return node;
    }

    private ObjectNode buildAuctionWon(NotificationEvent event) {
        Map<String, String> payload = event.getPayload();
        ObjectNode node = baseNode(AUCTION_WON, event);
        putLongField(node, "finalPrice", payload, "finalPrice", "highestBid");
        return node;
    }

    private ObjectNode buildOutbid(NotificationEvent event) {
        Map<String, String> payload = event.getPayload();
        ObjectNode node = baseNode(OUTBID, event);
        putLongField(node, "currentPrice", payload, "currentPrice", "newHighestBid");
        return node;
    }

    private ObjectNode buildBidRejected(NotificationEvent event) {
        Map<String, String> payload = event.getPayload();
        ObjectNode node = baseNode(BID_REJECTED, event);
        putLongField(node, "rejectedPrice", payload, "rejectedPrice");
        putTextField(node, "reason", payload, "reason");
        return node;
    }

    private ObjectNode baseNode(String type, NotificationEvent event) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("type", type);
        node.put("auctionId", event.getAuctionId().toString());
        node.put("occurredAt", formatOccurredAt(event.getOccurredAt()));
        return node;
    }

    /** epoch 초·밀리초 모두 수용해 API ISO-8601 문자열로 변환 */
    private String formatOccurredAt(long occurredAt) {
        Instant instant = occurredAt > 1_000_000_000_000L
                ? Instant.ofEpochMilli(occurredAt)
                : Instant.ofEpochSecond(occurredAt);
        return ISO_LOCAL.format(instant);
    }

    private boolean putLongField(ObjectNode node, String field, Map<String, String> payload, String... keys) {
        for (String key : keys) {
            String value = payload.get(key);
            if (value != null && !value.isBlank()) {
                node.put(field, Long.parseLong(value));
                return true;
            }
        }
        return false;
    }

    private boolean putTextField(ObjectNode node, String field, Map<String, String> payload, String... keys) {
        for (String key : keys) {
            String value = payload.get(key);
            if (value != null && !value.isBlank()) {
                node.put(field, value);
                return true;
            }
        }
        return false;
    }

    private String toJson(ObjectNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("WebSocket 메시지 JSON 직렬화 실패", e);
        }
    }
}
