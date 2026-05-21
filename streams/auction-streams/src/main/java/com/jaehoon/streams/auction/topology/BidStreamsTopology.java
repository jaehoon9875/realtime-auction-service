package com.jaehoon.streams.auction.topology;

import static com.jaehoon.streams.auction.constants.StreamsConstants.*;

import com.jaehoon.auction.avro.BidDeadLetterEvent;
import com.jaehoon.auction.avro.BidEvent;
import com.jaehoon.auction.events.NotificationEvent;
import com.jaehoon.streams.auction.processor.BidStateProcessor;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.Branched;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Grouped;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Named;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.kstream.TimeWindows;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/** bid-events 토픽 소비 → notification-events 발행 토폴로지를 구성하는 컴포넌트. */
@Slf4j
@Component
@RequiredArgsConstructor
public class BidStreamsTopology {

        private final StreamsBuilder builder;
        private final Serde<BidEvent> bidEventSerde;
        private final Serde<NotificationEvent> notificationEventSerde;
        private final Serde<BidDeadLetterEvent> bidDeadLetterEventSerde;

        /**
         * 입찰 이벤트 처리 토폴로지를 등록한다.
         *
         * BID_PLACED : BidStateProcessor로 최고가 State Store를 갱신하고 OUTBID 알림 발행.
         * BID_REJECTED : 거부된 입찰자 개인 알림 발행.
         * unknown : 알 수 없는 이벤트 타입은 bid-dead-letter 토픽으로 라우팅.
         * 1분 window 내 10건 이상 급증 시 경고 로그 출력. (알림 미발행.)
         */
        @PostConstruct
        public void buildTopology() {
                Map<String, KStream<String, BidEvent>> branches = builder
                                // bid-events 토픽 소비
                                .stream(TOPIC_BID_EVENTS, Consumed.with(Serdes.String(), bidEventSerde))
                                .filter((key, event) -> event != null)
                                // BID_PLACED, BID_REJECTED 이벤트 분기
                                .split(Named.as("bid-"))
                                .branch((key, event) -> EVENT_BID_PLACED.equals(event.getEventType()),
                                                Branched.as("placed"))
                                .branch((key, event) -> EVENT_BID_REJECTED.equals(event.getEventType()),
                                                Branched.as("rejected"))
                                .defaultBranch(Branched.as("unknown"));

                KStream<String, BidEvent> validBids = branches.get("bid-placed");
                KStream<String, BidEvent> rejectedBids = branches.get("bid-rejected");
                KStream<String, BidEvent> unknownBids = branches.get("bid-unknown");

                // BidStateProcessor는 최고가 State Store를 갱신하고 notification-events를 발행한다.
                validBids
                                // STORE_HIGHEST_BID는 AuctionStreamsTopology에서 선언됨
                                .process(BidStateProcessor::new, Named.as(PROCESSOR_BID_STATE), STORE_HIGHEST_BID)
                                .to(TOPIC_NOTIFICATION_EVENTS, Produced.with(Serdes.String(), notificationEventSerde));

                rejectedBids
                                .mapValues(event -> NotificationEvent.newBuilder()
                                                .setEventId(UUID.randomUUID().toString())
                                                .setNotificationType(NOTIFICATION_BID_REJECTED)
                                                .setTargetUserId(event.getBidderId())
                                                .setTargetAuctionId(null) // 개인 알림이므로 경매 대상 없음
                                                .setAuctionId(event.getAuctionId())
                                                .setPayload(Map.of(
                                                                "rejectedPrice", String.valueOf(event.getAmount()),
                                                                "reason", "PRICE_TOO_LOW"))
                                                .setOccurredAt(event.getOccurredAt())
                                                .build())
                                .to(TOPIC_NOTIFICATION_EVENTS, Produced.with(Serdes.String(), notificationEventSerde));

                unknownBids
                                .mapValues(event -> {
                                        log.warn("알 수 없는 bid 이벤트 타입 — DLQ로 라우팅. eventType={}, eventId={}",
                                                        event.getEventType(), event.getEventId());
                                        return BidDeadLetterEvent.newBuilder()
                                                        .setOriginalEvent(event)
                                                        .setFailureReason("UNKNOWN_EVENT_TYPE: " + event.getEventType())
                                                        .setFailedAt(System.currentTimeMillis())
                                                        .build();
                                })
                                .to(TOPIC_BID_DEAD_LETTER, Produced.with(Serdes.String(), bidDeadLetterEventSerde));

                // 급증 탐지는 BID_PLACED 기준으로만 집계
                validBids
                                .groupByKey(Grouped.with(Serdes.String(), bidEventSerde))
                                .windowedBy(TimeWindows.ofSizeWithNoGrace(Duration.ofMinutes(1)))
                                .count()
                                .toStream()
                                .filter((windowedKey, count) -> count != null && count >= 10)
                                .foreach((windowedKey, count) -> log.warn("입찰 급증 탐지. auctionId={}, 1분 내 입찰 수={}",
                                                windowedKey.key(), count));
        }
}
