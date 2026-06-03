package com.jaehoon.streams.auction.topology;

import tools.jackson.databind.json.JsonMapper;
import com.jaehoon.auction.avro.BidDeadLetterEvent;
import com.jaehoon.auction.avro.BidEvent;
import com.jaehoon.auction.events.NotificationEvent;
import com.jaehoon.streams.auction.config.StateStoreConfig;
import com.jaehoon.streams.auction.store.AuctionBidState;
import com.jaehoon.streams.auction.store.AuctionMetadata;
import io.confluent.kafka.schemaregistry.client.MockSchemaRegistryClient;
import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig;
import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.TestOutputTopic;
import org.apache.kafka.streams.TopologyTestDriver;
import org.apache.kafka.streams.state.KeyValueStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import static com.jaehoon.streams.auction.constants.StreamsConstants.*;
import static org.assertj.core.api.Assertions.assertThat;

class BidStreamsTopologyTest {

    private static final Instant BASE_TIME = Instant.parse("2026-01-01T00:00:00Z");

    private TopologyTestDriver testDriver;
    private TestInputTopic<String, BidEvent> bidInput;
    private TestOutputTopic<String, NotificationEvent> notificationOutput;
    private TestOutputTopic<String, BidDeadLetterEvent> dlqOutput;
    private KeyValueStore<String, AuctionBidState> bidStateStore;

    @BeforeEach
    void setUp() {
        MockSchemaRegistryClient mockRegistry = new MockSchemaRegistryClient();
        Map<String, Object> schemaConf = Map.of(
                AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, "mock://test"
        );

        SpecificAvroSerde<BidEvent> bidSerde = new SpecificAvroSerde<>(mockRegistry);
        bidSerde.configure(schemaConf, false);

        SpecificAvroSerde<NotificationEvent> notificationSerde = new SpecificAvroSerde<>(mockRegistry);
        notificationSerde.configure(schemaConf, false);

        SpecificAvroSerde<BidDeadLetterEvent> dlqSerde = new SpecificAvroSerde<>(mockRegistry);
        dlqSerde.configure(schemaConf, false);

        JsonMapper mapper = JsonMapper.builder().build();
        Serde<AuctionMetadata> metadataSerde = jsonSerde(mapper, AuctionMetadata.class);
        Serde<AuctionBidState> bidStateSerde = jsonSerde(mapper, AuctionBidState.class);

        StreamsBuilder builder = new StreamsBuilder();

        // StateStoreConfig가 STORE_AUCTION_METADATA, STORE_HIGHEST_BID를 선언
        new StateStoreConfig(builder, metadataSerde, bidStateSerde).registerStateStores();

        BidStreamsTopology bidTopology = new BidStreamsTopology(builder, bidSerde, notificationSerde, dlqSerde);
        bidTopology.buildTopology();

        Properties streamsProps = new Properties();
        streamsProps.put(StreamsConfig.APPLICATION_ID_CONFIG, "test-bid-streams");
        streamsProps.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "dummy:1234");

        testDriver = new TopologyTestDriver(builder.build(), streamsProps);

        bidInput = testDriver.createInputTopic(
                TOPIC_BID_EVENTS, new StringSerializer(), bidSerde.serializer()
        );
        notificationOutput = testDriver.createOutputTopic(
                TOPIC_NOTIFICATION_EVENTS, new StringDeserializer(), notificationSerde.deserializer()
        );
        dlqOutput = testDriver.createOutputTopic(
                TOPIC_BID_DEAD_LETTER, new StringDeserializer(), dlqSerde.deserializer()
        );
        bidStateStore = testDriver.getKeyValueStore(STORE_HIGHEST_BID);
    }

    @AfterEach
    void tearDown() {
        testDriver.close();
    }

    @Test
    void 최초_입찰은_스토어에_저장되고_OUTBID_없이_BID_UPDATED만_발행된다() {
        bidInput.pipeInput("auction-1", bidEvent("auction-1", "bidder-A", 5000L));

        AuctionBidState state = bidStateStore.get("auction-1");
        assertThat(state).isNotNull();
        assertThat(state.highestBid()).isEqualTo(5000L);
        assertThat(state.highestBidderId()).isEqualTo("bidder-A");
        assertThat(state.bidCount()).isEqualTo(1);

        // 최초 입찰: OUTBID 없이 BID_UPDATED 1건만 발행
        List<NotificationEvent> notifications = notificationOutput.readValuesToList();
        assertThat(notifications).hasSize(1);
        assertThat(notifications.get(0).getNotificationType().toString()).isEqualTo(NOTIFICATION_BID_UPDATED);
    }

    @Test
    void 더_높은_입찰은_스토어를_갱신하고_OUTBID와_BID_UPDATED를_발행한다() {
        bidInput.pipeInput("auction-1", bidEvent("auction-1", "bidder-A", 5000L));
        bidInput.pipeInput("auction-1", bidEvent("auction-1", "bidder-B", 8000L));

        AuctionBidState state = bidStateStore.get("auction-1");
        assertThat(state.highestBid()).isEqualTo(8000L);
        assertThat(state.highestBidderId()).isEqualTo("bidder-B");
        assertThat(state.bidCount()).isEqualTo(2);

        // 1번째 입찰: BID_UPDATED(1), 2번째 입찰: OUTBID + BID_UPDATED(2) → 총 3건
        List<NotificationEvent> notifications = notificationOutput.readValuesToList();
        assertThat(notifications).hasSize(3);
        assertThat(notifications.stream()
                .filter(n -> n.getNotificationType().toString().equals(NOTIFICATION_OUTBID))
                .count()).isEqualTo(1);
        assertThat(notifications.stream()
                .filter(n -> n.getNotificationType().toString().equals(NOTIFICATION_BID_UPDATED))
                .count()).isEqualTo(2);
    }

    @Test
    void OUTBID_알림의_수신자는_이전_최고_입찰자다() {
        bidInput.pipeInput("auction-1", bidEvent("auction-1", "bidder-A", 5000L));
        bidInput.pipeInput("auction-1", bidEvent("auction-1", "bidder-B", 8000L));

        NotificationEvent outbid = notificationOutput.readValuesToList().stream()
                .filter(n -> n.getNotificationType().toString().equals(NOTIFICATION_OUTBID))
                .findFirst().orElseThrow();
        assertThat(outbid.getTargetUserId().toString()).isEqualTo("bidder-A");
        assertThat(outbid.getAuctionId().toString()).isEqualTo("auction-1");
        assertThat(outbid.getPayload()).containsKey("newHighestBid");
        assertThat(outbid.getPayload().get("newHighestBid")).isEqualTo("8000");
    }

    @Test
    void 현재_최고가_이하_입찰은_스토어를_변경하지_않고_추가_알림도_발행하지_않는다() {
        bidInput.pipeInput("auction-1", bidEvent("auction-1", "bidder-A", 5000L));
        bidInput.pipeInput("auction-1", bidEvent("auction-1", "bidder-B", 3000L));

        AuctionBidState state = bidStateStore.get("auction-1");
        assertThat(state.highestBid()).isEqualTo(5000L);
        assertThat(state.highestBidderId()).isEqualTo("bidder-A");
        assertThat(state.bidCount()).isEqualTo(1);

        // 첫 입찰에서 BID_UPDATED 1건만 발행, 이하 입찰은 알림 없음
        List<NotificationEvent> notifications = notificationOutput.readValuesToList();
        assertThat(notifications).hasSize(1);
        assertThat(notifications.get(0).getNotificationType().toString()).isEqualTo(NOTIFICATION_BID_UPDATED);
    }

    @Test
    void BID_REJECTED_이벤트는_스토어를_변경하지_않고_거부_알림을_발행한다() {
        BidEvent rejected = BidEvent.newBuilder()
                .setEventId("ev-1")
                .setEventType("BID_REJECTED")
                .setBidId("bid-1")
                .setAuctionId("auction-1")
                .setBidderId("bidder-A")
                .setAmount(5000L)
                .setOccurredAt(BASE_TIME.toEpochMilli())
                .build();

        bidInput.pipeInput("auction-1", rejected);

        // State Store는 변경되지 않아야 함
        assertThat(bidStateStore.get("auction-1")).isNull();

        // 거부된 입찰자에게 개인 알림이 발행되어야 함
        List<NotificationEvent> notifications = notificationOutput.readValuesToList();
        assertThat(notifications).hasSize(1);
        assertThat(notifications.get(0).getNotificationType().toString()).isEqualTo(NOTIFICATION_BID_REJECTED);
        assertThat(notifications.get(0).getTargetUserId().toString()).isEqualTo("bidder-A");
    }

    @Test
    void 연속_입찰_갱신_시_마지막_최고_입찰자만_스토어에_남는다() {
        bidInput.pipeInput("auction-1", bidEvent("auction-1", "bidder-A", 5000L));
        bidInput.pipeInput("auction-1", bidEvent("auction-1", "bidder-B", 8000L));
        bidInput.pipeInput("auction-1", bidEvent("auction-1", "bidder-C", 12000L));

        AuctionBidState state = bidStateStore.get("auction-1");
        assertThat(state.highestBid()).isEqualTo(12000L);
        assertThat(state.highestBidderId()).isEqualTo("bidder-C");
        assertThat(state.bidCount()).isEqualTo(3);

        // A: BID_UPDATED(1), B: OUTBID+BID_UPDATED(2), C: OUTBID+BID_UPDATED(3) → 총 5건
        List<NotificationEvent> notifications = notificationOutput.readValuesToList();
        assertThat(notifications).hasSize(5);
        assertThat(notifications.stream()
                .filter(n -> n.getNotificationType().toString().equals(NOTIFICATION_OUTBID))
                .count()).isEqualTo(2);
        assertThat(notifications.stream()
                .filter(n -> n.getNotificationType().toString().equals(NOTIFICATION_BID_UPDATED))
                .count()).isEqualTo(3);
    }

    @Test
    void 서로_다른_경매의_입찰은_독립적으로_관리된다() {
        bidInput.pipeInput("auction-1", bidEvent("auction-1", "bidder-A", 5000L));
        bidInput.pipeInput("auction-2", bidEvent("auction-2", "bidder-B", 9000L));

        assertThat(bidStateStore.get("auction-1").highestBid()).isEqualTo(5000L);
        assertThat(bidStateStore.get("auction-2").highestBid()).isEqualTo(9000L);

        // 각 경매 첫 입찰마다 BID_UPDATED 1건씩 → 총 2건, OUTBID 없음
        List<NotificationEvent> notifications = notificationOutput.readValuesToList();
        assertThat(notifications).hasSize(2);
        assertThat(notifications.stream()
                .filter(n -> n.getNotificationType().toString().equals(NOTIFICATION_BID_UPDATED))
                .count()).isEqualTo(2);
        assertThat(notifications.stream()
                .filter(n -> n.getNotificationType().toString().equals(NOTIFICATION_OUTBID))
                .count()).isEqualTo(0);
    }

    @Test
    void BID_UPDATED_이벤트는_경매방_채널_라우팅용_auctionId와_현재가_입찰수를_포함한다() {
        // 시나리오: 최초 입찰 → BID_UPDATED에 targetAuctionId, currentPrice, bidCount 포함 검증
        bidInput.pipeInput("auction-1", bidEvent("auction-1", "bidder-A", 7000L));

        NotificationEvent bidUpdated = notificationOutput.readValuesToList().get(0);
        assertThat(bidUpdated.getNotificationType().toString()).isEqualTo(NOTIFICATION_BID_UPDATED);
        assertThat(bidUpdated.getTargetAuctionId().toString()).isEqualTo("auction-1");
        assertThat(bidUpdated.getAuctionId().toString()).isEqualTo("auction-1");
        assertThat(bidUpdated.getTargetUserId()).isNull();
        assertThat(bidUpdated.getPayload().get("currentPrice")).isEqualTo("7000");
        assertThat(bidUpdated.getPayload().get("bidCount")).isEqualTo("1");
    }

    @Test
    void BID_UPDATED_입찰수는_누적_증가한다() {
        // 시나리오: 연속 입찰 시 bidCount가 누적 증가하는지 확인
        bidInput.pipeInput("auction-1", bidEvent("auction-1", "bidder-A", 5000L));
        bidInput.pipeInput("auction-1", bidEvent("auction-1", "bidder-B", 9000L));

        List<NotificationEvent> bidUpdatedEvents = notificationOutput.readValuesToList().stream()
                .filter(n -> n.getNotificationType().toString().equals(NOTIFICATION_BID_UPDATED))
                .toList();

        assertThat(bidUpdatedEvents).hasSize(2);
        assertThat(bidUpdatedEvents.get(0).getPayload().get("bidCount")).isEqualTo("1");
        assertThat(bidUpdatedEvents.get(0).getPayload().get("currentPrice")).isEqualTo("5000");
        assertThat(bidUpdatedEvents.get(1).getPayload().get("bidCount")).isEqualTo("2");
        assertThat(bidUpdatedEvents.get(1).getPayload().get("currentPrice")).isEqualTo("9000");
    }

    @Test
    void 알_수_없는_이벤트_타입은_DLQ로_라우팅되고_알림은_발행되지_않는다() {
        BidEvent unknown = BidEvent.newBuilder()
                .setEventId("ev-unknown-1")
                .setEventType("UNKNOWN_TYPE")
                .setBidId("bid-unknown-1")
                .setAuctionId("auction-1")
                .setBidderId("bidder-A")
                .setAmount(5000L)
                .setOccurredAt(BASE_TIME.toEpochMilli())
                .build();

        bidInput.pipeInput("auction-1", unknown);

        // notification-events에는 아무것도 발행되지 않아야 함
        assertThat(notificationOutput.isEmpty()).isTrue();
        // State Store도 변경되지 않아야 함
        assertThat(bidStateStore.get("auction-1")).isNull();

        // DLQ에 원본 이벤트와 실패 사유가 담겨야 함
        List<BidDeadLetterEvent> dlqEvents = dlqOutput.readValuesToList();
        assertThat(dlqEvents).hasSize(1);
        BidDeadLetterEvent dlqEvent = dlqEvents.get(0);
        assertThat(dlqEvent.getOriginalEvent().getEventId().toString()).isEqualTo("ev-unknown-1");
        assertThat(dlqEvent.getFailureReason().toString()).contains("UNKNOWN_EVENT_TYPE");
        assertThat(dlqEvent.getFailedAt()).isPositive();
    }

    private BidEvent bidEvent(String auctionId, String bidderId, long amount) {
        return BidEvent.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setEventType(EVENT_BID_PLACED)
                .setBidId("bid-" + bidderId)
                .setAuctionId(auctionId)
                .setBidderId(bidderId)
                .setAmount(amount)
                .setOccurredAt(BASE_TIME.toEpochMilli())
                .build();
    }

    private <T> Serde<T> jsonSerde(JsonMapper mapper, Class<T> type) {
        return Serdes.serdeFrom(
                (topic, data) -> {
                    try { return mapper.writeValueAsBytes(data); }
                    catch (Exception e) { throw new SerializationException(e); }
                },
                (topic, bytes) -> {
                    if (bytes == null) return null;
                    try { return mapper.readValue(bytes, type); }
                    catch (Exception e) { throw new SerializationException(e); }
                }
        );
    }
}
