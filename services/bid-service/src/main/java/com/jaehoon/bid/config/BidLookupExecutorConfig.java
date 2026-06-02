package com.jaehoon.bid.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 입찰 검증 외부 조회를 병렬 실행할 Virtual Thread Executor를 설정한다.
 */
@Configuration
public class BidLookupExecutorConfig {

    @Bean(destroyMethod = "close")
    ExecutorService bidLookupExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
