package com.jaehoon.streams.auction.config;

import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    /**
     * 멀티 인스턴스 IQ 시 담당 peer로 동일 REST 경로를 재호출할 때 사용한다.
     * 기본 RestClient는 연결/읽기 대기가 사실상 무제한이므로, peer 장애 시 스레드 점유를 막기 위해 타임아웃을 둔다.
     */
    @Bean
    public RestClient restClient(AuctionStreamsProperties props) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(props.iqPeerConnectTimeoutMs()));
        factory.setReadTimeout(Duration.ofMillis(props.iqPeerReadTimeoutMs()));
        return RestClient.builder().requestFactory(factory).build();
    }
}
