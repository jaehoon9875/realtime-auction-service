package com.jaehoon.streams.auction.topology;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaehoon.auction.events.AuctionEvent;
import com.jaehoon.auction.events.NotificationEvent;
import com.jaehoon.streams.auction.config.AuctionStreamsProperties;
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

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static com.jaehoon.streams.auction.constants.StreamsConstants.*;
import static org.assertj.core.api.Assertions.assertThat;

class AuctionStreamsTopologyTest {

    private TopologyTestDriver testDriver;
    private TestInputTopic<String, AuctionEvent> auctionInput;
    private TestOutputTopic<String, NotificationEvent> notificationOutput;
    private KeyValueStore<String, AuctionMetadata> metadataStore;
    private KeyValueStore<String, AuctionBidState> bidStateStore;

    @BeforeEach
    void setUp() {
        MockSchemaRegistryClient mockRegistry = new MockSchemaRegistryClient();
        Map<String, Object> schemaConf = Map.of(
                AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, "mock://test"
        );

        SpecificAvroSerde<AuctionEvent> auctionSerde = new SpecificAvroSerde<>(mockRegistry);
        auctionSerde.configure(schemaConf, false);

        SpecificAvroSerde<NotificationEvent> notificationSerde = new SpecificAvroSerde<>(mockRegistry);
        notificationSerde.configure(schemaConf, false);

        ObjectMapper mapper = new ObjectMapper();
        Serde<AuctionMetadata> metadataSerde = jsonSerde(mapper, AuctionMetadata.class);
        Serde<AuctionBidState> bidStateSerde = jsonSerde(mapper, AuctionBidState.class);

        // punctuatorIntervalSeconds=1 로 짧게 설정해 테스트에서 빠르게 발화
        AuctionStreamsProperties props = new AuctionStreamsProperties(1, 3, 2000, 3000);
        StreamsBuilder builder = new StreamsBuilder();

        AuctionStreamsTopology topology = new AuctionStreamsTopology(
                builder, props, auctionSerde, metadataSerde, bidStateSerde, notificationSerde
        );
        topology.buildTopology();

        Properties streamsProps = new Properties();
        streamsProps.put(StreamsConfig.APPLICATION_ID_CONFIG, "test-auction-streams");
        streamsProps.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "dummy:1234");

        testDriver = new TopologyTestDriver(builder.build(), streamsProps);

        auctionInput = testDriver.createInputTopic(
                TOPIC_AUCTION_EVENTS, new StringSerializer(), auctionSerde.serializer()
        );
        notificationOutput = testDriver.createOutputTopic(
                TOPIC_NOTIFICATION_EVENTS, new StringDeserializer(), notificationSerde.deserializer()
        );
        metadataStore = testDriver.getKeyValueStore(STORE_AUCTION_METADATA);
        bidStateStore = testDriver.getKeyValueStore(STORE_HIGHEST_BID);
    }

    @AfterEach
    void tearDown() {
        testDriver.close();
    }

    @Test
    void AUCTION_CREATED_이벤트는_메타데이터_스토어에_저장된다() {
        long futureEndsAt = Instant.now().plusSeconds(3600).toEpochMilli();
        auctionInput.pipeInput("auction-1", auctionEvent("auction-1", EVENT_AUCTION_CREATED, futureEndsAt));

        AuctionMetadata saved = metadataStore.get("auction-1");

        assertThat(saved).isNotNull();
        assertThat(saved.startPrice()).isEqualTo(1000L);
        assertThat(saved.sellerId()).isEqualTo("seller-1");
        assertThat(saved.title()).isEqualTo("테스트 경매");
        assertThat(saved.endsAt()).isEqualTo(futureEndsAt);
    }

    @Test
    void AUCTION_CREATED_아닌_이벤트는_스토어에_저장되지_않는다() {
        auctionInput.pipeInput("auction-1",
                auctionEvent("auction-1", "AUCTION_UPDATED", Instant.now().plusSeconds(3600).toEpochMilli()));

        assertThat(metadataStore.get("auction-1")).isNull();
        assertThat(notificationOutput.isEmpty()).isTrue();
    }

    @Test
    void 마감된_경매는_AUCTION_CLOSED_알림이_발행된다() {
        long pastEndsAt = Instant.now().minusSeconds(60).toEpochMilli();
        auctionInput.pipeInput("auction-1", auctionEvent("auction-1", EVENT_AUCTION_CREATED, pastEndsAt));

        testDriver.advanceWallClockTime(Duration.ofSeconds(2));

        List<NotificationEvent> notifications = notificationOutput.readValuesToList();
        assertThat(notifications)
                .anyMatch(e -> NOTIFICATION_AUCTION_CLOSED.equals(e.getNotificationType()));
    }

    @Test
    void 마감_후_메타데이터는_스토어에서_삭제된다() {
        long pastEndsAt = Instant.now().minusSeconds(60).toEpochMilli();
        auctionInput.pipeInput("auction-1", auctionEvent("auction-1", EVENT_AUCTION_CREATED, pastEndsAt));

        testDriver.advanceWallClockTime(Duration.ofSeconds(2));

        // 마감 처리 후 재처리 방지를 위해 메타데이터 삭제
        assertThat(metadataStore.get("auction-1")).isNull();
    }

    @Test
    void 낙찰자가_있는_마감_경매는_AUCTION_WON과_AUCTION_CLOSED_모두_발행된다() {
        long pastEndsAt = Instant.now().minusSeconds(60).toEpochMilli();
        auctionInput.pipeInput("auction-1", auctionEvent("auction-1", EVENT_AUCTION_CREATED, pastEndsAt));
        // BidStreamsTopology 미구현 상태이므로 낙찰자 상태를 직접 주입
        bidStateStore.put("auction-1", new AuctionBidState(50000L, "bidder-99", 3));

        testDriver.advanceWallClockTime(Duration.ofSeconds(2));

        List<NotificationEvent> notifications = notificationOutput.readValuesToList();
        assertThat(notifications)
                .anyMatch(e -> NOTIFICATION_AUCTION_WON.equals(e.getNotificationType()))
                .anyMatch(e -> NOTIFICATION_AUCTION_CLOSED.equals(e.getNotificationType()));
    }

    @Test
    void AUCTION_WON_알림의_수신자는_낙찰자_ID다() {
        long pastEndsAt = Instant.now().minusSeconds(60).toEpochMilli();
        auctionInput.pipeInput("auction-1", auctionEvent("auction-1", EVENT_AUCTION_CREATED, pastEndsAt));
        bidStateStore.put("auction-1", new AuctionBidState(50000L, "bidder-99", 3));

        testDriver.advanceWallClockTime(Duration.ofSeconds(2));

        NotificationEvent wonEvent = notificationOutput.readValuesToList().stream()
                .filter(e -> NOTIFICATION_AUCTION_WON.equals(e.getNotificationType()))
                .findFirst().orElseThrow();

        assertThat(wonEvent.getTargetUserId().toString()).isEqualTo("bidder-99");
        assertThat(wonEvent.getAuctionId().toString()).isEqualTo("auction-1");
    }

    @Test
    void AUCTION_CLOSED_알림의_수신자는_경매_ID다() {
        // notification-service가 auctionId를 기준으로 구독자에게 라우팅하는 구조
        long pastEndsAt = Instant.now().minusSeconds(60).toEpochMilli();
        auctionInput.pipeInput("auction-1", auctionEvent("auction-1", EVENT_AUCTION_CREATED, pastEndsAt));

        testDriver.advanceWallClockTime(Duration.ofSeconds(2));

        NotificationEvent closedEvent = notificationOutput.readValuesToList().stream()
                .filter(e -> NOTIFICATION_AUCTION_CLOSED.equals(e.getNotificationType()))
                .findFirst().orElseThrow();

        assertThat(closedEvent.getTargetUserId().toString()).isEqualTo("auction-1");
    }

    @Test
    void 낙찰자가_없는_마감_경매는_AUCTION_CLOSED만_발행된다() {
        long pastEndsAt = Instant.now().minusSeconds(60).toEpochMilli();
        auctionInput.pipeInput("auction-1", auctionEvent("auction-1", EVENT_AUCTION_CREATED, pastEndsAt));
        // bidStateStore에 아무것도 넣지 않음 = 낙찰자 없음

        testDriver.advanceWallClockTime(Duration.ofSeconds(2));

        List<NotificationEvent> notifications = notificationOutput.readValuesToList();
        assertThat(notifications).noneMatch(e -> NOTIFICATION_AUCTION_WON.equals(e.getNotificationType()));
        assertThat(notifications).anyMatch(e -> NOTIFICATION_AUCTION_CLOSED.equals(e.getNotificationType()));
    }

    @Test
    void 아직_마감되지_않은_경매는_Punctuator_발화_후에도_알림이_발행되지_않는다() {
        long futureEndsAt = Instant.now().plusSeconds(3600).toEpochMilli();
        auctionInput.pipeInput("auction-1", auctionEvent("auction-1", EVENT_AUCTION_CREATED, futureEndsAt));

        testDriver.advanceWallClockTime(Duration.ofSeconds(2));

        assertThat(notificationOutput.isEmpty()).isTrue();
        assertThat(metadataStore.get("auction-1")).isNotNull(); // 삭제되지 않음
    }

    private AuctionEvent auctionEvent(String auctionId, String eventType, long endsAt) {
        return AuctionEvent.newBuilder()
                .setEventId("ev-" + auctionId)
                .setEventType(eventType)
                .setAuctionId(auctionId)
                .setSellerId("seller-1")
                .setTitle("테스트 경매")
                .setStartPrice(1000L)
                .setEndsAt(endsAt)
                .setOccurredAt(Instant.now().toEpochMilli())
                .build();
    }

    private <T> Serde<T> jsonSerde(ObjectMapper mapper, Class<T> type) {
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
