package com.jaehoon.streams.auction.config;

import org.apache.kafka.streams.state.HostInfo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * 이 JVM이 Kafka Streams 설정 {@code application.server}에 선언한 host:port와,
 * {@code queryMetadataForKey}로 얻은 담당 호스트가 같은지 비교한다. (멀티 인스턴스 IQ 라우팅)
 */
@Component
public class LocalInteractiveQueryHost {

    private final HostInfo hostInfo;

    /** {@code application.server} 문자열을 파싱해 로컬 {@link HostInfo}로 보관한다. */
    public LocalInteractiveQueryHost(
            Environment environment,
            @Value("${spring.kafka.streams.properties.application.server}") String applicationServer) {
        this.hostInfo = parse(applicationServer);
        validateNonLocalProfileAddress(environment, this.hostInfo);
    }

    /** {@code host:port} 한 줄을 {@link HostInfo}로 변환한다. */
    private static HostInfo parse(String applicationServer) {
        int lastColon = applicationServer.lastIndexOf(':');
        if (lastColon <= 0 || lastColon == applicationServer.length() - 1) {
            throw new IllegalStateException(
                    "spring.kafka.streams.properties.application.server 은 host:port 형식이어야 합니다. 값="
                            + applicationServer);
        }
        String host = applicationServer.substring(0, lastColon);
        String portPart = applicationServer.substring(lastColon + 1);
        final int port;
        try {
            port = Integer.parseInt(portPart);
        } catch (NumberFormatException e) {
            throw new IllegalStateException(
                    "spring.kafka.streams.properties.application.server 의 port는 숫자여야 합니다. 값="
                            + applicationServer,
                    e);
        }
        return new HostInfo(host, port);
    }

    /**
     * local 프로필이 아닌 환경에서는 localhost를 금지한다.
     * 멀티 인스턴스 IQ에서 localhost가 퍼지면 peer 라우팅이 자신으로 잘못 향할 수 있다.
     */
    private static void validateNonLocalProfileAddress(Environment environment, HostInfo hostInfo) {
        boolean localProfileActive = false;
        for (String profile : environment.getActiveProfiles()) {
            if ("local".equalsIgnoreCase(profile)) {
                localProfileActive = true;
                break;
            }
        }
        if (!localProfileActive && "localhost".equalsIgnoreCase(hostInfo.host())) {
            throw new IllegalStateException(
                    "local 프로필 외 환경에서는 spring.kafka.streams.properties.application.server 에 localhost를 사용할 수 없습니다. 값="
                            + hostInfo.host() + ":" + hostInfo.port());
        }
    }

    /** {@code owner}가 이 인스턴스의 {@code application.server}와 동일하면 true (로컬 스토어 조회 가능). */
    public boolean isLocal(HostInfo owner) {
        return hostInfo.host().equalsIgnoreCase(owner.host()) && hostInfo.port() == owner.port();
    }
}
