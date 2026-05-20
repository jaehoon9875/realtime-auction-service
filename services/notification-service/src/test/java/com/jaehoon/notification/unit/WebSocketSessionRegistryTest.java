package com.jaehoon.notification.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jaehoon.notification.session.RedisSessionStore;
import com.jaehoon.notification.session.WebSocketSessionRegistry;
import com.jaehoon.notification.support.WebSocketTestSupport;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * WebSocketSessionRegistry 로컬 세션·Redis 라우팅 검증.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WebSocketSessionRegistryTest {

    private static final String INSTANCE_ID = "test-instance";

    @Mock
    private RedisSessionStore redisSessionStore;

    private WebSocketSessionRegistry registry;

    @BeforeEach
    void setUp() {
        when(redisSessionStore.getInstanceId()).thenReturn(INSTANCE_ID);
        // register* 시 비동기 Redis 호출 — 반환 Mono가 null이면 NPE
        lenient()
                .when(redisSessionStore.addAuctionSession(anyString(), anyString()))
                .thenReturn(Mono.empty());
        lenient()
                .when(redisSessionStore.setUserSession(anyString(), anyString()))
                .thenReturn(Mono.empty());
        registry = new WebSocketSessionRegistry(redisSessionStore);
    }

    @Test
    void sendToLocalSession_연결된_세션에_메시지를_전송한다() {
        List<String> received = new CopyOnWriteArrayList<>();
        WebSocketSession session = WebSocketTestSupport.mockSession("session-1", received);
        registry.registerAuctionSession("auction-1", session);

        StepVerifier.create(registry.sendToLocalSession("session-1", "{\"type\":\"PING\"}"))
                .verifyComplete();

        assertThat(received).containsExactly("{\"type\":\"PING\"}");
    }

    @Test
    void sendToAuction_로컬_인스턴스_세션이면_직접_전송한다() {
        List<String> received = new CopyOnWriteArrayList<>();
        WebSocketSession session = WebSocketTestSupport.mockSession("session-1", received);
        registry.registerAuctionSession("auction-1", session);

        when(redisSessionStore.getAuctionSessionTargets("auction-1"))
                .thenReturn(
                        Flux.just(new RedisSessionStore.SessionTarget(INSTANCE_ID, "session-1")));

        StepVerifier.create(registry.sendToAuction("auction-1", "{\"type\":\"BID_UPDATED\"}"))
                .verifyComplete();

        assertThat(received).containsExactly("{\"type\":\"BID_UPDATED\"}");
        verify(redisSessionStore, never()).publishNotify(anyString(), anyString(), anyString());
    }

    @Test
    void sendToAuction_다른_인스턴스_세션이면_PubSub으로_라우팅한다() {
        when(redisSessionStore.getAuctionSessionTargets("auction-2"))
                .thenReturn(
                        Flux.just(new RedisSessionStore.SessionTarget("other-instance", "session-remote")));
        when(redisSessionStore.publishNotify("other-instance", "session-remote", "{\"type\":\"BID_UPDATED\"}"))
                .thenReturn(Mono.empty());

        StepVerifier.create(registry.sendToAuction("auction-2", "{\"type\":\"BID_UPDATED\"}"))
                .verifyComplete();

        verify(redisSessionStore)
                .publishNotify("other-instance", "session-remote", "{\"type\":\"BID_UPDATED\"}");
    }

    @Test
    void sendToUser_로컬_세션에_전송한다() {
        List<String> received = new CopyOnWriteArrayList<>();
        WebSocketSession session = WebSocketTestSupport.mockSession("user-session", received);
        registry.registerUserSession("user-1", session);

        when(redisSessionStore.getUserSessionTarget("user-1"))
                .thenReturn(
                        Mono.just(new RedisSessionStore.SessionTarget(INSTANCE_ID, "user-session")));

        StepVerifier.create(registry.sendToUser("user-1", "{\"type\":\"OUTBID\"}"))
                .verifyComplete();

        assertThat(received).containsExactly("{\"type\":\"OUTBID\"}");
    }

    @Test
    void removeAuctionSession_로컬_맵에서_세션을_제거한다() {
        List<String> received = new CopyOnWriteArrayList<>();
        WebSocketSession session = WebSocketTestSupport.mockSession("session-1", received);
        registry.registerAuctionSession("auction-1", session);
        when(redisSessionStore.removeAuctionSession("auction-1", "session-1"))
                .thenReturn(Mono.empty());

        registry.removeAuctionSession("auction-1", "session-1");

        StepVerifier.create(registry.sendToLocalSession("session-1", "gone"))
                .verifyComplete();
        assertThat(received).isEmpty();
    }
}
