package com.jaehoon.streams.auction.topology;

import static com.jaehoon.streams.auction.constants.StreamsConstants.*;

import com.jaehoon.auction.events.AuctionEvent;
import com.jaehoon.auction.events.NotificationEvent;
import com.jaehoon.streams.auction.config.AuctionStreamsProperties;
import com.jaehoon.streams.auction.processor.AuctionMetadataProcessor;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Named;
import org.apache.kafka.streams.kstream.Produced;
import org.springframework.stereotype.Component;

/**
 * 경매가 만료됐는지 주기적으로 확인하고, 만료되면 낙찰/마감 알림을 발행하는 토폴로지.
 */
@Component
@RequiredArgsConstructor
public class AuctionStreamsTopology {

    private final StreamsBuilder builder;
    private final AuctionStreamsProperties properties;
    private final Serde<AuctionEvent> auctionEventSerde;
    private final Serde<NotificationEvent> notificationEventSerde;

    /**
     * 경매 이벤트 처리 토폴로지를 등록한다.
     *
     * auction-events 토픽에서 AUCTION_CREATED 이벤트만 필터링해 AuctionMetadataProcessor로 전달한다.
     * Processor는 메타데이터를 State Store에 저장하고, Punctuator가 주기적으로 만료 경매를 감지해
     * AUCTION_CLOSED · AUCTION_WON 이벤트를 notification-events 토픽으로 발행한다.
     */
    @PostConstruct
    public void buildTopology() {
        builder.stream(TOPIC_AUCTION_EVENTS, Consumed.with(Serdes.String(), auctionEventSerde))
                .filter((key, event) -> event != null && EVENT_AUCTION_CREATED.equals(event.getEventType()))
                .process(
                        () -> new AuctionMetadataProcessor(properties.punctuatorIntervalSeconds()),
                        Named.as(PROCESSOR_AUCTION_METADATA),
                        STORE_AUCTION_METADATA, STORE_HIGHEST_BID)
                .to(TOPIC_NOTIFICATION_EVENTS, Produced.with(Serdes.String(), notificationEventSerde));
    }
}
