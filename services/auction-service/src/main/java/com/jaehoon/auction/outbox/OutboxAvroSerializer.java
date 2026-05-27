package com.jaehoon.auction.outbox;

import org.apache.avro.specific.SpecificRecord;
import org.springframework.stereotype.Component;

import io.confluent.kafka.serializers.KafkaAvroSerializer;
import lombok.RequiredArgsConstructor;

/**
 * Outbox payload를 Confluent Avro wire format(bytea)으로 직렬화한다.
 * Debezium BinaryDataConverter가 Kafka로 그대로 전달하고 Streams가 역직렬화한다.
 */
@Component
@RequiredArgsConstructor
public class OutboxAvroSerializer {

    public static final String AUCTION_EVENTS_TOPIC = "auction-events";

    private final KafkaAvroSerializer kafkaAvroSerializer;

    /**
     * @param event {@code AuctionEvent.avsc}에 대응하는 Avro 레코드
     * @return Schema Registry schema ID가 포함된 Avro 바이너리
     */
    public byte[] serialize(SpecificRecord event) {
        return kafkaAvroSerializer.serialize(AUCTION_EVENTS_TOPIC, event);
    }
}
