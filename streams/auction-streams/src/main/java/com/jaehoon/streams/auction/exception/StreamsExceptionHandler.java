package com.jaehoon.streams.auction.exception;

import com.jaehoon.streams.auction.config.AuctionStreamsProperties;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.streams.errors.StreamsUncaughtExceptionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 스트림 처리 중 uncaught exception 발생 시 스레드 재시작 정책을 결정한다.
 * 연속 실패가 임계치를 초과하면 SHUTDOWN_CLIENT로 전환해 무한 루프를 방지한다.
 */
@Component
@RequiredArgsConstructor
public class StreamsExceptionHandler implements StreamsUncaughtExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(StreamsExceptionHandler.class);

    private final AuctionStreamsProperties properties;
    private final AtomicInteger failureCount = new AtomicInteger(0);

    @Override
    public StreamThreadExceptionResponse handle(Throwable exception) {
        int count = failureCount.incrementAndGet();
        log.error("Kafka Streams 스레드 예외 발생 ({}회째) — 스레드를 재시작합니다.", count, exception);

        // 연속 실패가 임계치를 초과하면 클라이언트 전체를 종료해 무한 재시작을 방지
        if (count >= properties.maxFailures()) {
            log.error("연속 실패 {}회 초과 — Streams 클라이언트를 종료합니다. 운영자 확인 필요.", properties.maxFailures());
            return StreamThreadExceptionResponse.SHUTDOWN_CLIENT;
        }

        return StreamThreadExceptionResponse.REPLACE_THREAD;
    }
}
