package com.jaehoon.notification.kafka;

/**
 * notification-events 토픽의 notificationType 값. Avro 스키마·docs/kafka.md와 동일하게 유지한다.
 */
public final class NotificationTypes {

    private NotificationTypes() {}

    public static final String BID_UPDATED = "BID_UPDATED";
    public static final String AUCTION_CLOSED = "AUCTION_CLOSED";
    public static final String AUCTION_WON = "AUCTION_WON";
    public static final String OUTBID = "OUTBID";
    public static final String BID_REJECTED = "BID_REJECTED";
}
