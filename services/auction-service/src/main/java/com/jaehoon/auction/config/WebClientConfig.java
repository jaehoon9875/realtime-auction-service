package com.jaehoon.auction.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@EnableConfigurationProperties(AuctionStreamsProperties.class)
public class WebClientConfig {

    /*
     * TODO [운영 강화 시 검토] auction-streams 호출용 WebClient
     *
     * 1) 타임아웃 (AuctionStreamsProperties의 connectTimeoutMs, readTimeoutMs 활용)
     *    - Reactor Netty HttpClient에 ChannelOption.CONNECT_TIMEOUT_MILLIS,
     *      responseTimeout(Duration.ofMillis(readTimeoutMs)) 설정 후
     *      new ReactorClientHttpConnector(httpClient)를 WebClient.builder()에 연결.
     *    - 전체 스트리밍 구간까지 세밀히 자르고 싶으면 읽기 타임아웃 정책을 추가로 검토.
     *
     * 2) Codec / 버퍼
     *    - 응답 JSON이 매우 크면 exchangeStrategies로 maxInMemorySize 조정.
     *
     * 3) 관측(Micrometer / Observation)
     *    - 지연·에러 메트릭·트레이싱 연동 시 WebClient Observation 또는
     *      Boot 자동 설정(observation enabled) 검토.
     *
     * 4) 연결 풀
     *    - 기본 Netty 풀로 충분한 경우가 많음. 초당 호출이 매우 높으면
     *      maxConnections, pendingAcquireMaxCount 등 ConnectionProvider 튜닝.
     *
     * 5) Retry / Circuit Breaker
     *    - Resilience4j와 역할 분리: 필터에서 재시도할지, 서비스 레이어에서만 할지 정책 통일.
     *    - 동일 요청 이중 실행 주의(idempotent 한 경로에만 retry 등).
     *
     * 6) 헤더
     *    - 서비스 간 인증·스테이징 구분이 필요하면 defaultHeader(User-Agent, 내부 토큰 등).
     *
     * 7) 로깅
     *    - 요청/응답 바디 전체 DEBUG 로깅은 운영 부하·개인정보 이슈 가능.
     *      필요 시 경량 로깅 또는 trace/correlation ID만 전파.
     */
    @Bean
    public WebClient auctionStreamsWebClient(AuctionStreamsProperties auctionStreamsProperties) {
        return WebClient.builder()
                .baseUrl(auctionStreamsProperties.baseUrl())
                .build();
    }
}
