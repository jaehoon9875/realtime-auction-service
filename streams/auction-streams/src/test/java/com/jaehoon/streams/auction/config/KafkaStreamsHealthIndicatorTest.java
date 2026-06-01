package com.jaehoon.streams.auction.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StoreQueryParameters;
import org.apache.kafka.streams.errors.InvalidStateStoreException;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Status;
import org.springframework.kafka.config.StreamsBuilderFactoryBean;

class KafkaStreamsHealthIndicatorTest {

    private final StreamsBuilderFactoryBean factoryBean = mock(StreamsBuilderFactoryBean.class);
    private final KafkaStreams kafkaStreams = mock(KafkaStreams.class);
    private final KafkaStreamsHealthIndicator indicator = new KafkaStreamsHealthIndicator(factoryBean);

    @Test
    void health_KafkaStreams가초기화되지않으면_DOWN을반환한다() {
        // given: Kafka Streams 인스턴스 생성 전에는 IQ 요청을 처리할 수 없다.
        when(factoryBean.getKafkaStreams()).thenReturn(null);

        // when, then
        assertThat(indicator.health().getStatus()).isEqualTo(Status.DOWN);
    }

    @Test
    void health_KafkaStreams가RUNNING이아니면_DOWN을반환한다() {
        // given: 리밸런스 중에는 State Store 조회를 받지 않는다.
        when(factoryBean.getKafkaStreams()).thenReturn(kafkaStreams);
        when(kafkaStreams.state()).thenReturn(KafkaStreams.State.REBALANCING);

        // when, then
        assertThat(indicator.health().getStatus()).isEqualTo(Status.DOWN);
    }

    @Test
    void health_StateStore가조회불가능하면_DOWN을반환한다() {
        // given: RUNNING 직후라도 Store 복구가 끝나지 않았으면 준비 상태가 아니다.
        when(factoryBean.getKafkaStreams()).thenReturn(kafkaStreams);
        when(kafkaStreams.state()).thenReturn(KafkaStreams.State.RUNNING);
        when(kafkaStreams.store(anyStoreQueryParameters()))
                .thenThrow(new InvalidStateStoreException("store is not ready"));

        // when, then
        assertThat(indicator.health().getStatus()).isEqualTo(Status.DOWN);
    }

    @Test
    void health_KafkaStreams와StateStore가준비되면_UP을반환한다() {
        // given: 두 Store가 모두 조회 가능한 상태다.
        when(factoryBean.getKafkaStreams()).thenReturn(kafkaStreams);
        when(kafkaStreams.state()).thenReturn(KafkaStreams.State.RUNNING);
        when(kafkaStreams.store(anyStoreQueryParameters()))
                .thenReturn(mockStateStore());

        // when, then
        assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
    }

    @SuppressWarnings("unchecked")
    private static ReadOnlyKeyValueStore<Object, Object> mockStateStore() {
        return mock(ReadOnlyKeyValueStore.class);
    }

    private static StoreQueryParameters<ReadOnlyKeyValueStore<Object, Object>> anyStoreQueryParameters() {
        return org.mockito.ArgumentMatchers.any();
    }
}
