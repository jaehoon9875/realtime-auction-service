package com.jaehoon.streams.auction.exception;

import com.jaehoon.streams.auction.config.AuctionStreamsProperties;
import org.apache.kafka.streams.errors.StreamsUncaughtExceptionHandler.StreamThreadExceptionResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StreamsExceptionHandlerTest {

    private StreamsExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new StreamsExceptionHandler(new AuctionStreamsProperties(5, 3, 2000, 3000));
    }

    @Test
    void 임계치_미만_실패는_REPLACE_THREAD를_반환한다() {
        assertThat(handler.handle(new RuntimeException("1차 실패")))
                .isEqualTo(StreamThreadExceptionResponse.REPLACE_THREAD);
        assertThat(handler.handle(new RuntimeException("2차 실패")))
                .isEqualTo(StreamThreadExceptionResponse.REPLACE_THREAD);
    }

    @Test
    void 임계치_도달_시_SHUTDOWN_CLIENT를_반환한다() {
        // maxFailures=3: 1·2차는 REPLACE_THREAD, 3차에서 SHUTDOWN_CLIENT
        handler.handle(new RuntimeException("1차 실패"));
        handler.handle(new RuntimeException("2차 실패"));

        assertThat(handler.handle(new RuntimeException("3차 실패")))
                .isEqualTo(StreamThreadExceptionResponse.SHUTDOWN_CLIENT);
    }

    @Test
    void 임계치_초과_후에도_계속_SHUTDOWN_CLIENT를_반환한다() {
        handler.handle(new RuntimeException("1차"));
        handler.handle(new RuntimeException("2차"));
        handler.handle(new RuntimeException("3차")); // SHUTDOWN

        assertThat(handler.handle(new RuntimeException("4차")))
                .isEqualTo(StreamThreadExceptionResponse.SHUTDOWN_CLIENT);
    }

    @Test
    void maxFailures_1이면_첫_번째_실패에서_바로_SHUTDOWN_CLIENT를_반환한다() {
        StreamsExceptionHandler strictHandler = new StreamsExceptionHandler(
                new AuctionStreamsProperties(5, 1, 2000, 3000)
        );

        assertThat(strictHandler.handle(new RuntimeException("1차 실패")))
                .isEqualTo(StreamThreadExceptionResponse.SHUTDOWN_CLIENT);
    }
}
