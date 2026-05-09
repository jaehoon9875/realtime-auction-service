package com.jaehoon.streams.auction.topology;

import static com.jaehoon.streams.auction.constants.StreamsConstants.*;

import com.jaehoon.auction.avro.BidEvent;
import com.jaehoon.auction.events.NotificationEvent;
import com.jaehoon.streams.auction.processor.BidStateProcessor;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Grouped;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Named;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.kstream.TimeWindows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class BidStreamsTopology {

    private static final Logger log = LoggerFactory.getLogger(BidStreamsTopology.class);

    private final StreamsBuilder builder;
    private final Serde<BidEvent> bidEventSerde;
    private final Serde<NotificationEvent> notificationEventSerde;

    @PostConstruct
    public void buildTopology() {
        // BID_PLACED 이벤트만 처리 (BID_REJECTED는 무시)
        KStream<String, BidEvent> validBids = builder
                .stream(TOPIC_BID_EVENTS, Consumed.with(Serdes.String(), bidEventSerde))
                .filter((key, event) -> event != null && EVENT_BID_PLACED.equals(event.getEventType()));

        // 최고가 State Store 갱신 및 OUTBID 알림 발행
        // STORE_HIGHEST_BID는 AuctionStreamsTopology에서 선언됨
        validBids
                .process(BidStateProcessor::new, Named.as(PROCESSOR_BID_STATE), STORE_HIGHEST_BID)
                .to(TOPIC_NOTIFICATION_EVENTS, Produced.with(Serdes.String(), notificationEventSerde));

        // 1분 tumbling window 내 입찰 급증 탐지 (로깅 전용)
        validBids
                .groupByKey(Grouped.with(Serdes.String(), bidEventSerde))
                .windowedBy(TimeWindows.ofSizeWithNoGrace(Duration.ofMinutes(1)))
                .count()
                .toStream()
                .filter((windowedKey, count) -> count != null && count >= 10)
                .foreach((windowedKey, count) ->
                        log.warn("입찰 급증 탐지. auctionId={}, 1분 내 입찰 수={}", windowedKey.key(), count));
    }
}
