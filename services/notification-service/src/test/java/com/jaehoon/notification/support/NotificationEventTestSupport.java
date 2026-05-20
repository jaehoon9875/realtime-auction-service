package com.jaehoon.notification.support;

import com.jaehoon.auction.events.NotificationEvent;
import java.time.Instant;
import java.util.Map;

/**
 * Avro NotificationEvent 테스트 빌더 (필수 필드 기본값 포함).
 */
public final class NotificationEventTestSupport {

    public static final long OCCURRED_AT_SECONDS =
            Instant.parse("2026-01-15T12:00:00Z").getEpochSecond();

    public static final String OCCURRED_AT_ISO = "2026-01-15T12:00:00";

    private NotificationEventTestSupport() {}

    public static NotificationEvent.Builder baseBuilder(String type, String auctionId) {
        return NotificationEvent.newBuilder()
                .setEventId("evt-test")
                .setNotificationType(type)
                .setAuctionId(auctionId)
                .setPayload(Map.of())
                .setOccurredAt(OCCURRED_AT_SECONDS);
    }
}
