package com.jaehoon.bid.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.micrometer.context.ContextSnapshotFactory;

/**
 * 입찰 검증 외부 조회를 병렬 실행할 Virtual Thread Executor를 설정한다.
 */
@Configuration
public class BidLookupExecutorConfig {

    @Bean(destroyMethod = "close")
    ExecutorService bidLookupExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    // 병렬 조회 스레드에 요청 스레드의 트레이싱/MDC 컨텍스트를 전파하기 위한 스냅샷 팩토리.
    @Bean
    ContextSnapshotFactory contextSnapshotFactory() {
        return ContextSnapshotFactory.builder().build();
    }
}
