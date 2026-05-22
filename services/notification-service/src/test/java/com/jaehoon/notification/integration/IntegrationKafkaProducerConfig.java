package com.jaehoon.notification.integration;

import com.jaehoon.auction.events.NotificationEvent;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import io.confluent.kafka.serializers.KafkaAvroSerializerConfig;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

/**
 * 통합 테스트에서 notification-events 토픽으로 Avro 이벤트를 발행하기 위한 Kafka Producer 설정.
 * notification-service 운영 코드는 Consumer만 사용하므로 테스트 전용으로 둔다.
 */
@TestConfiguration
public class IntegrationKafkaProducerConfig {

  @Value("${spring.kafka.bootstrap-servers}")
  private String bootstrapServers;

  @Value("${spring.kafka.producer.properties.schema.registry.url}")
  private String schemaRegistryUrl;

  /**
   * notification-events 통합 테스트용 ProducerFactory를 생성한다.
   */
  @Bean
  ProducerFactory<String, NotificationEvent> notificationEventProducerFactory() {
    Map<String, Object> props = new HashMap<>();
    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer.class);
    props.put(KafkaAvroSerializerConfig.SCHEMA_REGISTRY_URL_CONFIG, schemaRegistryUrl);
    props.put(KafkaAvroSerializerConfig.AUTO_REGISTER_SCHEMAS, true);
    return new DefaultKafkaProducerFactory<>(props);
  }

  /**
   * notification-events 통합 테스트용 KafkaTemplate을 생성한다.
   */
  @Bean
  KafkaTemplate<String, NotificationEvent> notificationEventKafkaTemplate(
      ProducerFactory<String, NotificationEvent> notificationEventProducerFactory) {
    return new KafkaTemplate<>(notificationEventProducerFactory);
  }
}
