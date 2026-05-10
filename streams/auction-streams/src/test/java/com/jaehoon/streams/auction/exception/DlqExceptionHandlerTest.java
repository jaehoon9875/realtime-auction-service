package com.jaehoon.streams.auction.exception;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.streams.errors.DeserializationExceptionHandler.DeserializationHandlerResponse;
import org.apache.kafka.streams.processor.ProcessorContext;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static com.jaehoon.streams.auction.constants.StreamsConstants.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * DlqExceptionHandler는 configure()에서 KafkaProducer를 직접 생성하므로
 * Mockito.mockConstruction()으로 생성자를 가로채 단위 테스트한다.
 */
@SuppressWarnings("rawtypes") // mockConstruction(KafkaProducer.class) 로 인한 raw type 불가피
class DlqExceptionHandlerTest {

    @Test
    void bid_이벤트_역직렬화_실패는_bid_dead_letter_토픽으로_라우팅된다() {
        try (MockedConstruction<KafkaProducer> mocked = Mockito.mockConstruction(KafkaProducer.class)) {
            DlqExceptionHandler handler = createAndConfigure();

            handle(handler, "bid-events");

            assertSentToDlqTopic(mocked, TOPIC_BID_DEAD_LETTER);
        }
    }

    @Test
    void auction_이벤트_역직렬화_실패는_auction_dead_letter_토픽으로_라우팅된다() {
        try (MockedConstruction<KafkaProducer> mocked = Mockito.mockConstruction(KafkaProducer.class)) {
            DlqExceptionHandler handler = createAndConfigure();

            handle(handler, "auction-events");

            assertSentToDlqTopic(mocked, TOPIC_AUCTION_DEAD_LETTER);
        }
    }

    @Test
    void 알_수_없는_토픽은_기본_dead_letter_토픽으로_라우팅된다() {
        try (MockedConstruction<KafkaProducer> mocked = Mockito.mockConstruction(KafkaProducer.class)) {
            DlqExceptionHandler handler = createAndConfigure();

            handle(handler, "unknown-events");

            assertSentToDlqTopic(mocked, TOPIC_DEAD_LETTER);
        }
    }

    @Test
    void 역직렬화_실패_후_항상_CONTINUE를_반환한다() {
        try (MockedConstruction<KafkaProducer> ignored = Mockito.mockConstruction(KafkaProducer.class)) {
            DlqExceptionHandler handler = createAndConfigure();

            DeserializationHandlerResponse response = handle(handler, "bid-events");

            assertThat(response).isEqualTo(DeserializationHandlerResponse.CONTINUE);
        }
    }

    @Test
    void DLQ_레코드에_소스_토픽_헤더가_포함된다() {
        try (MockedConstruction<KafkaProducer> mocked = Mockito.mockConstruction(KafkaProducer.class)) {
            DlqExceptionHandler handler = createAndConfigure();

            handle(handler, "bid-events");

            ProducerRecord<?, ?> sentRecord = captureRecord(mocked);
            String sourceTopic = new String(
                    sentRecord.headers().lastHeader("source-topic").value(),
                    StandardCharsets.UTF_8
            );
            assertThat(sourceTopic).isEqualTo("bid-events");
        }
    }

    @Test
    void DLQ_레코드에_에러_메시지_헤더가_포함된다() {
        try (MockedConstruction<KafkaProducer> mocked = Mockito.mockConstruction(KafkaProducer.class)) {
            DlqExceptionHandler handler = createAndConfigure();

            ConsumerRecord<byte[], byte[]> record = consumerRecord("bid-events");
            handler.handle(mock(ProcessorContext.class), record, new RuntimeException("invalid avro schema"));

            ProducerRecord<?, ?> sentRecord = captureRecord(mocked);
            String errorMessage = new String(
                    sentRecord.headers().lastHeader("error-message").value(),
                    StandardCharsets.UTF_8
            );
            assertThat(errorMessage).contains("invalid avro schema");
        }
    }

    private DlqExceptionHandler createAndConfigure() {
        DlqExceptionHandler handler = new DlqExceptionHandler();
        handler.configure(Map.of("bootstrap.servers", "dummy:1234"));
        return handler;
    }

    private DeserializationHandlerResponse handle(DlqExceptionHandler handler, String sourceTopic) {
        return handler.handle(
                mock(ProcessorContext.class),
                consumerRecord(sourceTopic),
                new RuntimeException("bad data")
        );
    }

    private ConsumerRecord<byte[], byte[]> consumerRecord(String topic) {
        return new ConsumerRecord<>(topic, 0, 0L,
                "key".getBytes(StandardCharsets.UTF_8),
                "bad-value".getBytes(StandardCharsets.UTF_8));
    }

    private void assertSentToDlqTopic(MockedConstruction<KafkaProducer> mocked, String expectedTopic) {
        ProducerRecord<?, ?> record = captureRecord(mocked);
        assertThat(record.topic()).isEqualTo(expectedTopic);
    }

    @SuppressWarnings("unchecked") // ArgumentCaptor<ProducerRecord> raw → ProducerRecord<?,?> 변환
    private ProducerRecord<?, ?> captureRecord(MockedConstruction<KafkaProducer> mocked) {
        KafkaProducer<?, ?> mockProducer = mocked.constructed().get(0);
        ArgumentCaptor<ProducerRecord> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(mockProducer).send(captor.capture(), any());
        return captor.getValue();
    }
}
