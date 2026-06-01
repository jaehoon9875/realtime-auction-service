package com.jaehoon.streams.auction.config;

import static com.jaehoon.streams.auction.constants.StreamsConstants.STORE_AUCTION_METADATA;
import static com.jaehoon.streams.auction.constants.StreamsConstants.STORE_HIGHEST_BID;

import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StoreQueryParameters;
import org.apache.kafka.streams.state.QueryableStoreTypes;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.kafka.config.StreamsBuilderFactoryBean;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * Kafka Streams와 State Store가 Interactive Query 요청을 처리할 준비가 되었는지 확인한다.
 */
@Component("kafkaStreams")
@RequiredArgsConstructor
public class KafkaStreamsHealthIndicator implements HealthIndicator {

    private final StreamsBuilderFactoryBean streamsBuilderFactoryBean;

    /**
     * Kafka Streams 실행 상태와 State Store 조회 가능 여부를 readiness 상태로 반환한다.
     */
    @Override
    public Health health() {
        KafkaStreams streams = streamsBuilderFactoryBean.getKafkaStreams();
        if (streams == null) {
            return Health.down()
                    .withDetail("state", "NOT_INITIALIZED")
                    .build();
        }

        KafkaStreams.State state = streams.state();
        if (state != KafkaStreams.State.RUNNING) {
            return Health.down()
                    .withDetail("state", state.name())
                    .build();
        }

        try {
            verifyStoreQueryable(streams, STORE_AUCTION_METADATA);
            verifyStoreQueryable(streams, STORE_HIGHEST_BID);
            return Health.up()
                    .withDetail("state", state.name())
                    .build();
        } catch (RuntimeException e) {
            return Health.down(e)
                    .withDetail("state", state.name())
                    .build();
        }
    }

    private static void verifyStoreQueryable(KafkaStreams streams, String storeName) {
        // RUNNING 직후 리밸런스 중에는 State Store가 아직 조회 불가능할 수 있다.
        streams.store(StoreQueryParameters.fromNameAndType(storeName, QueryableStoreTypes.keyValueStore()));
    }
}
