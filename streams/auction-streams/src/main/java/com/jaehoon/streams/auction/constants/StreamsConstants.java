package com.jaehoon.streams.auction.constants;

public final class StreamsConstants {

    private StreamsConstants() {}

    // State Store 이름
    public static final String STORE_AUCTION_METADATA = "auction-metadata";
    public static final String STORE_HIGHEST_BID = "auction-highest-bid";

    // 소비 토픽
    public static final String TOPIC_AUCTION_EVENTS = "auction-events";
    public static final String TOPIC_BID_EVENTS = "bid-events";

    // 발행 토픽
    public static final String TOPIC_NOTIFICATION_EVENTS = "notification-events";
    public static final String TOPIC_BID_DEAD_LETTER = "bid-dead-letter";
    public static final String TOPIC_AUCTION_DEAD_LETTER = "auction-dead-letter";
    public static final String TOPIC_DEAD_LETTER = "dead-letter";

    // auction-events 이벤트 타입
    public static final String EVENT_AUCTION_CREATED = "AUCTION_CREATED";

    // bid-events 이벤트 타입
    public static final String EVENT_BID_PLACED = "BID_PLACED";
    public static final String EVENT_BID_REJECTED = "BID_REJECTED";

    // notification-events 알림 타입
    public static final String NOTIFICATION_AUCTION_WON = "AUCTION_WON";
    public static final String NOTIFICATION_AUCTION_CLOSED = "AUCTION_CLOSED";
    public static final String NOTIFICATION_OUTBID = "OUTBID";
    public static final String NOTIFICATION_BID_REJECTED = "BID_REJECTED";

    // Processor 이름
    public static final String PROCESSOR_AUCTION_METADATA = "auction-metadata-processor";
    public static final String PROCESSOR_BID_STATE = "bid-state-processor";
}
