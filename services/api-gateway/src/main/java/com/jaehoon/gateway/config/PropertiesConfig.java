package com.jaehoon.gateway.config;

import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Configuration;

/**
 * config 패키지의 {@link org.springframework.boot.context.properties.ConfigurationProperties} 빈을 등록한다.
 * 클래스를 개별 나열하지 않아 Properties 추가 시 이 설정을 수정할 필요가 없다.
 */
@Configuration
@ConfigurationPropertiesScan
public class PropertiesConfig {
}
