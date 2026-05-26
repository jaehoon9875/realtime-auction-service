package com.jaehoon.streams.auction.exception;

import static com.jaehoon.streams.auction.constants.StreamsConstants.*;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.streams.errors.DeserializationExceptionHandler;
import org.apache.kafka.streams.errors.ErrorHandlerContext;
import org.apache.kafka.streams.errors.ProductionExceptionHandler;
import org.apache.kafka.streams.processor.ProcessorContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Properties;

/**
 * 역직렬화·프로듀스 실패 이벤트를 DLQ 토픽으로 라우팅한다.
 * Kafka Streams가 리플렉션으로 직접 인스턴스화하므로 Spring Bean이 아닌 일반 클래스로 작성한다.
 */
public class DlqExceptionHandler implements DeserializationExceptionHandler, ProductionExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(DlqExceptionHandler.class);

    private KafkaProducer<byte[], byte[]> producer;

    @Override
    public void configure(Map<String, ?> configs) {
        // ExceptionHandler는 Kafka Streams 설정 시 configure()로 초기화된다.
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, configs.get("bootstrap.servers"));
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
        // DLQ 메시지는 at-least-once로 충분. EOS 트랜잭션 밖에서 발행.
        props.put(ProducerConfig.ACKS_CONFIG, "1");
        this.producer = new KafkaProducer<>(props);
    }

    @Override
    public DeserializationHandlerResponse handle(ProcessorContext context,
                                                  ConsumerRecord<byte[], byte[]> record,
                                                  Exception exception) {
        String dlqTopic = resolveDlqTopicForSource(record.topic());
        log.error("역직렬화 실패 — source: {}, offset: {}, dlq: {}", record.topic(), record.offset(), dlqTopic, exception);

        sendToDlq(
                dlqTopic,
                record.key(),
                record.value(),
                record.topic(),
                record.partition(),
                record.offset(),
                exception
        );

        // CONTINUE: 해당 레코드를 건너뛰고 다음 레코드 처리 재개
        return DeserializationHandlerResponse.CONTINUE;
    }

    @Override
    public ProductionExceptionHandlerResponse handle(ErrorHandlerContext context,
                                                     ProducerRecord<byte[], byte[]> record,
                                                     Exception exception) {
        String dlqTopic = resolveDlqTopicForProduction(record.topic());
        log.error(
                "프로듀스 실패 — target: {}, source: {}-{}-{}, dlq: {}",
                record.topic(),
                context.topic(),
                context.partition(),
                context.offset(),
                dlqTopic,
                exception
        );

        sendToDlq(
                dlqTopic,
                record.key(),
                record.value(),
                context.topic(),
                context.partition(),
                context.offset(),
                exception
        );

        return ProductionExceptionHandlerResponse.CONTINUE;
    }

    @Override
    @SuppressWarnings("rawtypes")
    public ProductionExceptionHandlerResponse handleSerializationException(ErrorHandlerContext context,
                                                                         ProducerRecord record,
                                                                         Exception exception,
                                                                         SerializationExceptionOrigin origin) {
        String dlqTopic = resolveDlqTopicForProduction(record.topic());
        log.error(
                "프로듀스 직렬화 실패 — target: {}, source: {}-{}-{}, origin: {}, dlq: {}",
                record.topic(),
                context.topic(),
                context.partition(),
                context.offset(),
                origin,
                dlqTopic,
                exception
        );

        sendToDlq(
                dlqTopic,
                (byte[]) record.key(),
                (byte[]) record.value(),
                context.topic(),
                context.partition(),
                context.offset(),
                exception
        );

        return ProductionExceptionHandlerResponse.CONTINUE;
    }

    private void sendToDlq(String dlqTopic,
                           byte[] key,
                           byte[] value,
                           String sourceTopic,
                           int sourcePartition,
                           long sourceOffset,
                           Exception exception) {
        try {
            ProducerRecord<byte[], byte[]> dlqRecord = new ProducerRecord<>(dlqTopic, key, value);
            // 원본 메타데이터와 에러 정보를 헤더에 포함
            dlqRecord.headers()
                    .add("source-topic", sourceTopic.getBytes(StandardCharsets.UTF_8))
                    .add("source-partition", String.valueOf(sourcePartition).getBytes(StandardCharsets.UTF_8))
                    .add("source-offset", String.valueOf(sourceOffset).getBytes(StandardCharsets.UTF_8))
                    .add("error-message", String.valueOf(exception).getBytes(StandardCharsets.UTF_8))
                    .add("failed-at", String.valueOf(System.currentTimeMillis()).getBytes(StandardCharsets.UTF_8));
            producer.send(dlqRecord, (metadata, ex) -> {
                if (ex != null) {
                    log.error("DLQ 발행 실패 — 이벤트 유실 가능성 있음. topic: {}", dlqTopic, ex);
                }
            });
        } catch (Exception e) {
            log.error("DLQ 레코드 생성 실패 — 이벤트 유실 가능성 있음. topic: {}", dlqTopic, e);
        }
    }

    // 소스 토픽명과 정확히 일치할 때만 DLQ를 매핑한다(접두어 매칭은 토픽 확장 시 오매핑 위험).
    private String resolveDlqTopicForSource(String sourceTopic) {
        if (TOPIC_BID_EVENTS.equals(sourceTopic)) {
            return TOPIC_BID_DEAD_LETTER;
        }
        if (TOPIC_AUCTION_EVENTS.equals(sourceTopic)) {
            return TOPIC_AUCTION_DEAD_LETTER;
        }
        return TOPIC_DEAD_LETTER;
    }

    private String resolveDlqTopicForProduction(String targetTopic) {
        // 현재 downstream 출력은 notification-events뿐이며 모두 공통 dead-letter로 라우팅한다.
        return TOPIC_DEAD_LETTER;
    }
}
