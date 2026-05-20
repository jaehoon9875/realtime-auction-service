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

@Component
@RequiredArgsConstructor
public class AuctionStreamsTopology {

    private final StreamsBuilder builder;
    private final AuctionStreamsProperties properties;
    private final Serde<AuctionEvent> auctionEventSerde;
    private final Serde<NotificationEvent> notificationEventSerde;

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
