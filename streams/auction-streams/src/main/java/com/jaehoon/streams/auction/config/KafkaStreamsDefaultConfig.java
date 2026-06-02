package com.jaehoon.streams.auction.config;

import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.streams.StreamsConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.KafkaStreamsDefaultConfiguration;
import org.springframework.kafka.config.KafkaStreamsConfiguration;

/**
 * Spring Boot 4.x에서 Kafka Streams auto-configuration이 제거됨에 따라
 * @EnableKafkaStreams 사용 시 필수인 defaultKafkaStreamsConfig 빈을 직접 선언한다.
 */
@Configuration
public class KafkaStreamsDefaultConfig {

    @Bean(name = KafkaStreamsDefaultConfiguration.DEFAULT_STREAMS_CONFIG_BEAN_NAME)
    KafkaStreamsConfiguration defaultKafkaStreamsConfig(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers,
            @Value("${spring.kafka.streams.application-id}") String applicationId,
            @Value("${spring.kafka.streams.properties.processing.guarantee:exactly_once_v2}") String processingGuarantee,
            @Value("${spring.kafka.streams.properties.schema.registry.url}") String schemaRegistryUrl,
            @Value("${spring.kafka.streams.properties.default.key.serde:org.apache.kafka.common.serialization.Serdes$StringSerde}") String defaultKeySerde,
            @Value("${spring.kafka.streams.properties.state.dir:/tmp/kafka-streams}") String stateDir,
            @Value("${spring.kafka.streams.properties.application.server}") String applicationServer,
            @Value("${spring.kafka.streams.properties.default.deserialization.exception.handler}") String deserializationExceptionHandler,
            @Value("${spring.kafka.streams.properties.default.production.exception.handler}") String productionExceptionHandler) {

        Map<String, Object> props = new HashMap<>();
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, applicationId);
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, defaultKeySerde);
        props.put(StreamsConfig.STATE_DIR_CONFIG, stateDir);
        props.put(StreamsConfig.APPLICATION_SERVER_CONFIG, applicationServer);
        props.put("processing.guarantee", processingGuarantee);
        props.put("schema.registry.url", schemaRegistryUrl);
        props.put(StreamsConfig.DESERIALIZATION_EXCEPTION_HANDLER_CLASS_CONFIG, deserializationExceptionHandler);
        props.put(StreamsConfig.PRODUCTION_EXCEPTION_HANDLER_CLASS_CONFIG, productionExceptionHandler);
        return new KafkaStreamsConfiguration(props);
    }
}
