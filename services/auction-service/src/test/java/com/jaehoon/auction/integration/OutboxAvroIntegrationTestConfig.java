package com.jaehoon.auction.integration;

import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;

import io.confluent.kafka.schemaregistry.client.MockSchemaRegistryClient;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import io.confluent.kafka.serializers.KafkaAvroSerializerConfig;

@TestConfiguration
@Profile("integration")
public class OutboxAvroIntegrationTestConfig {

    private static final String MOCK_REGISTRY_URL = "mock://outbox-test";

    @Bean
    MockSchemaRegistryClient mockSchemaRegistryClient() {
        return new MockSchemaRegistryClient();
    }

    @Bean
    KafkaAvroSerializer kafkaAvroSerializer(MockSchemaRegistryClient schemaRegistryClient) {
        KafkaAvroSerializer serializer = new KafkaAvroSerializer(schemaRegistryClient);
        Map<String, Object> config = new HashMap<>();
        config.put(KafkaAvroSerializerConfig.SCHEMA_REGISTRY_URL_CONFIG, MOCK_REGISTRY_URL);
        config.put(KafkaAvroSerializerConfig.AUTO_REGISTER_SCHEMAS, true);
        serializer.configure(config, false);
        return serializer;
    }

    @Bean
    KafkaAvroDeserializer outboxAvroDeserializer(MockSchemaRegistryClient schemaRegistryClient) {
        KafkaAvroDeserializer deserializer = new KafkaAvroDeserializer(schemaRegistryClient);
        Map<String, Object> config = new HashMap<>();
        config.put(KafkaAvroDeserializerConfig.SCHEMA_REGISTRY_URL_CONFIG, MOCK_REGISTRY_URL);
        config.put(KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG, true);
        deserializer.configure(config, false);
        return deserializer;
    }
}
