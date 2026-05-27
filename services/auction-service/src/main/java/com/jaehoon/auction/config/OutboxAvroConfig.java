package com.jaehoon.auction.config;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import io.confluent.kafka.serializers.KafkaAvroSerializerConfig;

@Configuration
@Profile("!integration")
public class OutboxAvroConfig {

    @Bean(destroyMethod = "close")
    public KafkaAvroSerializer kafkaAvroSerializer(
            @Value("${app.outbox.schema-registry-url}") String schemaRegistryUrl) {
        KafkaAvroSerializer serializer = new KafkaAvroSerializer();
        serializer.configure(
                Map.of(
                        AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, schemaRegistryUrl,
                        KafkaAvroSerializerConfig.AUTO_REGISTER_SCHEMAS, true
                ),
                false
        );
        return serializer;
    }
}
