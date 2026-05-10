package com.jaehoon.streams.auction.config;

import java.io.IOException;
import java.util.Map;

import org.apache.avro.specific.SpecificRecord;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.StreamsBuilderFactoryBeanConfigurer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaehoon.auction.avro.BidEvent;
import com.jaehoon.auction.events.AuctionEvent;
import com.jaehoon.auction.events.NotificationEvent;
import com.jaehoon.streams.auction.exception.StreamsExceptionHandler;
import com.jaehoon.streams.auction.store.AuctionBidState;
import com.jaehoon.streams.auction.store.AuctionMetadata;

import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig;
import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde;
import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(AuctionStreamsProperties.class)
public class StreamsSerdeConfig {

    // Schema Registry 주소는 application.yml의 SCHEMA_REGISTRY_URL 환경변수 설정을 따른다.
    @Value("${spring.kafka.streams.properties.schema.registry.url:http://localhost:${SCHEMA_REGISTRY_PORT:8085}}")
    private String schemaRegistryUrl;

    private final ObjectMapper objectMapper;

    // Kafka 토픽 이벤트는 Avro + Schema Registry 기반으로 직렬화/역직렬화한다.
    @Bean
    public Serde<AuctionEvent> auctionEventSerde() {
        return specificAvroSerde(false);
    }

    @Bean
    public Serde<BidEvent> bidEventSerde() {
        return specificAvroSerde(false);
    }

    @Bean
    public Serde<NotificationEvent> notificationEventSerde() {
        return specificAvroSerde(false);
    }

    // State Store 내부 상태는 토픽 이벤트가 아니므로 앱 전용 JSON Serde로 보관한다.
    @Bean
    public Serde<AuctionBidState> auctionBidStateSerde() {
        return jsonSerde(AuctionBidState.class);
    }

    @Bean
    public Serde<AuctionMetadata> auctionMetadataSerde() {
        return jsonSerde(AuctionMetadata.class);
    }

    // StreamsUncaughtExceptionHandler는 application.yml로 등록이 불가하므로 KafkaStreams 인스턴스에 직접 연결한다.
    @Bean
    public StreamsBuilderFactoryBeanConfigurer streamsExceptionHandlerConfigurer(StreamsExceptionHandler handler) {
        return factoryBean -> factoryBean.setKafkaStreamsCustomizer(
                kafkaStreams -> kafkaStreams.setUncaughtExceptionHandler(handler)
        );
    }

    private <T extends SpecificRecord> SpecificAvroSerde<T> specificAvroSerde(boolean isKey) {
        SpecificAvroSerde<T> serde = new SpecificAvroSerde<>();
        serde.configure(
                Map.of(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, schemaRegistryUrl),
                isKey
        );
        return serde;
    }

    private <T> Serde<T> jsonSerde(Class<T> targetType) {
        return Serdes.serdeFrom(
                (topic, data) -> serializeJson(data),
                (topic, data) -> deserializeJson(targetType, data)
        );
    }

    private <T> byte[] serializeJson(T data) {
        if (data == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsBytes(data);
        } catch (IOException e) {
            throw new SerializationException("State Store value JSON serialization failed", e);
        }
    }

    private <T> T deserializeJson(Class<T> targetType, byte[] data) {
        if (data == null) {
            return null;
        }
        try {
            return objectMapper.readValue(data, targetType);
        } catch (IOException e) {
            throw new SerializationException("State Store value JSON deserialization failed", e);
        }
    }
}
