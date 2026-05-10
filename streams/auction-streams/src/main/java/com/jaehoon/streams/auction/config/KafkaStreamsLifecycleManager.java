package com.jaehoon.streams.auction.config;

import java.time.Duration;

import org.apache.kafka.streams.KafkaStreams;
import org.springframework.context.SmartLifecycle;
import org.springframework.kafka.config.StreamsBuilderFactoryBean;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaStreamsLifecycleManager implements SmartLifecycle {

    private static final Duration CLOSE_TIMEOUT = Duration.ofSeconds(30);

    private final StreamsBuilderFactoryBean streamsBuilderFactoryBean;

    private volatile boolean running;

    @Override
    public void start() {
        running = true;
    }

    @Override
    public void stop() {
        shutdownKafkaStreams();
        running = false;
    }

    @Override
    public void stop(Runnable callback) {
        try {
            stop();
        } finally {
            callback.run();
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }

    @Override
    public int getPhase() {
        // Spring 종료 단계에서 늦게 호출되어 다른 빈 종료 전에 Streams를 안전하게 close 하도록 한다.
        return Integer.MAX_VALUE;
    }

    private void shutdownKafkaStreams() {
        KafkaStreams kafkaStreams = streamsBuilderFactoryBean.getKafkaStreams();
        if (kafkaStreams == null) {
            log.debug("KafkaStreams instance is not initialized; skip shutdown");
            return;
        }

        KafkaStreams.State currentState = kafkaStreams.state();
        if (currentState == KafkaStreams.State.NOT_RUNNING) {
            log.debug("KafkaStreams is already stopped; skip shutdown");
            return;
        }
        if (currentState == KafkaStreams.State.ERROR) {
            // ERROR 상태에서도 close 는 안전하게 호출 가능하므로 종료는 진행하고, 운영 가시성을 위해 상태를 명시적으로 남긴다.
            log.warn("KafkaStreams is in ERROR state; proceed with graceful shutdown");
        }

        try {
            log.info("KafkaStreams shutdown started (state={}, timeout={})", currentState, CLOSE_TIMEOUT);
            // StreamsBuilderFactoryBean에 위임해 내부 상태(isRunning 등)도 일관되게 관리한다.
            streamsBuilderFactoryBean.setCloseTimeout((int) CLOSE_TIMEOUT.toSeconds());
            streamsBuilderFactoryBean.stop();
            log.info("KafkaStreams shutdown completed (timeout={})", CLOSE_TIMEOUT);
        } catch (Exception e) {
            log.warn("KafkaStreams graceful shutdown failed", e);
        }
    }
}
