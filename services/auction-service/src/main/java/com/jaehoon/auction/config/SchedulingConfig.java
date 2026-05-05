package com.jaehoon.auction.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 예약 경매 시작 시각 처리 등 타이머 작업 활성화 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.auction.schedule.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
