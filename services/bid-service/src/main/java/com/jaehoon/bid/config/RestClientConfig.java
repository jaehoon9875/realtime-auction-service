package com.jaehoon.bid.config;

import java.time.Duration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties({ AuctionServiceProperties.class, BidStreamsProperties.class })
public class RestClientConfig {

    @Bean
    RestClient auctionServiceRestClient(AuctionServiceProperties props, BidSecurityProperties securityProps) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(props.connectTimeoutMs()));
        factory.setReadTimeout(Duration.ofMillis(props.readTimeoutMs()));

        RestClient.Builder builder = RestClient.builder()
                .baseUrl(props.baseUrl())
                .requestFactory(factory);

        // 서비스 간 직접 호출 시 Gateway가 붙이는 내부 시크릿 헤더를 함께 전송한다.
        // auction-service의 InternalRequestTokenFilter가 이 헤더를 검증한다.
        if (StringUtils.hasText(securityProps.internalRequestSecret())) {
            builder.defaultHeader(securityProps.internalRequestHeaderName(), securityProps.internalRequestSecret());
        }

        return builder.build();
    }

    @Bean
    RestClient bidStreamsRestClient(BidStreamsProperties props) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(props.connectTimeoutMs()));
        factory.setReadTimeout(Duration.ofMillis(props.readTimeoutMs()));

        return RestClient.builder()
                .baseUrl(props.baseUrl())
                .requestFactory(factory)
                .build();
    }
}
