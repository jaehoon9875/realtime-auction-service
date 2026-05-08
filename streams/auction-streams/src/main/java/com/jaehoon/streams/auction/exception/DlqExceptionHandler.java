package com.jaehoon.streams.auction.exception;

import static com.jaehoon.streams.auction.constants.StreamsConstants.*;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.streams.errors.DeserializationExceptionHandler;
import org.apache.kafka.streams.processor.ProcessorContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Properties;

/**
 * 역직렬화 실패 이벤트를 DLQ 토픽으로 라우팅한다.
 * Kafka Streams가 리플렉션으로 직접 인스턴스화하므로 Spring Bean이 아닌 일반 클래스로 작성한다.
 */
public class DlqExceptionHandler implements DeserializationExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(DlqExceptionHandler.class);

    private KafkaProducer<byte[], byte[]> producer;

    @Override
    public void configure(Map<String, ?> configs) {
        // DeserializationExceptionHandler는 Kafka Streams 설정 시 configure()로 초기화된다.
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, configs.get("bootstrap.servers"));
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
        // 역직렬화 실패 메시지는 at-least-once로 충분. EOS 트랜잭션 밖에서 발행.
        props.put(ProducerConfig.ACKS_CONFIG, "1");
        this.producer = new KafkaProducer<>(props);
    }

    @Override
    public DeserializationHandlerResponse handle(ProcessorContext context,
                                                  ConsumerRecord<byte[], byte[]> record,
                                                  Exception exception) {
        String dlqTopic = resolveDlqTopic(record.topic());
        log.error("역직렬화 실패 — source: {}, offset: {}, dlq: {}", record.topic(), record.offset(), dlqTopic, exception);

        try {
            ProducerRecord<byte[], byte[]> dlqRecord = new ProducerRecord<>(dlqTopic, record.key(), record.value());
            // 원본 메타데이터와 에러 정보를 헤더에 포함
            dlqRecord.headers()
                    .add("source-topic", record.topic().getBytes(StandardCharsets.UTF_8))
                    .add("source-partition", String.valueOf(record.partition()).getBytes(StandardCharsets.UTF_8))
                    .add("source-offset", String.valueOf(record.offset()).getBytes(StandardCharsets.UTF_8))
                    .add("error-message", String.valueOf(exception).getBytes(StandardCharsets.UTF_8))
                    .add("failed-at", String.valueOf(System.currentTimeMillis()).getBytes(StandardCharsets.UTF_8));
            producer.send(dlqRecord);
        } catch (Exception e) {
            log.error("DLQ 발행 실패 — 이벤트 유실 가능성 있음. topic: {}", dlqTopic, e);
        }

        // CONTINUE: 해당 레코드를 건너뛰고 다음 레코드 처리 재개
        return DeserializationHandlerResponse.CONTINUE;
    }

    // 소스 토픽 이름 접두어로 대응하는 DLQ 토픽을 결정한다.
    private String resolveDlqTopic(String sourceTopic) {
        if (sourceTopic.startsWith("bid")) {
            return TOPIC_BID_DEAD_LETTER;
        }
        if (sourceTopic.startsWith("auction")) {
            return TOPIC_AUCTION_DEAD_LETTER;
        }
        return TOPIC_DEAD_LETTER;
    }
}
