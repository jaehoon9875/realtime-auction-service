package com.jaehoon.streams.auction.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    /** 멀티 인스턴스 IQ 시 담당 peer로 동일 REST 경로를 재호출할 때 사용한다. */
    @Bean
    public RestClient restClient(RestClient.Builder builder) {
        return builder.build();
    }
}
