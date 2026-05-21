package com.jaehoon.notification.integration;

import static com.jaehoon.notification.kafka.NotificationTypes.BID_REJECTED;
import static org.assertj.core.api.Assertions.assertThat;

import com.jaehoon.auction.events.NotificationEvent;
import com.jaehoon.notification.session.RedisSessionStore;
import com.jaehoon.notification.session.WebSocketSessionRegistry;
import com.jaehoon.notification.support.WebSocketTestSupport;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;
import com.redis.testcontainers.RedisContainer;

/**
 * Testcontainers(Kafka, Redis) 기반 notification-events → WebSocket 전달 통합 검증.
 * Docker 미기동 환경에서는 전체 클래스가 비활성화된다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("integration")
@Tag("integration")
@EnabledIf("com.jaehoon.notification.support.DockerConditions#isDockerAvailable")
class NotificationIntegrationTest {

    private static final String TOPIC = "notification-events";

    @Container
    @ServiceConnection
    static KafkaContainer kafka =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.7.0"));

    @Container
    @ServiceConnection
    static RedisContainer redis = new RedisContainer(RedisContainer.DEFAULT_IMAGE_NAME);

    @MockitoBean
    JwtDecoder jwtDecoder;

    @Autowired
    private WebSocketSessionRegistry sessionRegistry;

    @Autowired
    private RedisSessionStore redisSessionStore;

    @Autowired
    private KafkaTemplate<String, NotificationEvent> kafkaTemplate;

    @Autowired
    private ReactiveStringRedisTemplate reactiveRedis;

    @BeforeAll
    static void createTopic() {
        try (AdminClient admin =
                AdminClient.create(
                        Map.of(
                                "bootstrap.servers",
                                kafka.getBootstrapServers()))) {
            admin.createTopics(List.of(new NewTopic(TOPIC, 1, (short) 1))).all().get();
        } catch (Exception ignored) {
            // 토픽이 이미 있으면 무시
        }
    }

    @Test
    void BID_REJECTED_이벤트_소비_후_로컬_WebSocket_세션에_전달된다() {
        List<String> received = new CopyOnWriteArrayList<>();
        WebSocketSession session = WebSocketTestSupport.mockSession("ws-user-1", received);
        String userId = "user-integration-1";

        sessionRegistry.registerUserSession(userId, session);
        awaitRedisUserSession(userId);

        NotificationEvent event =
                NotificationEvent.newBuilder()
                        .setEventId("integration-evt-1")
                        .setNotificationType(BID_REJECTED)
                        .setTargetUserId(userId)
                        .setAuctionId("auction-integration-1")
                        .setPayload(
                                Map.of(
                                        "rejectedPrice", "1200000",
                                        "reason", "PRICE_TOO_LOW"))
                        .setOccurredAt(1_736_947_200L)
                        .build();

        kafkaTemplate.send(TOPIC, userId, event);

        Awaitility.await()
                .atMost(Duration.ofSeconds(15))
                .untilAsserted(
                        () -> {
                            assertThat(received).hasSize(1);
                            assertThat(received.getFirst()).contains(BID_REJECTED);
                            assertThat(received.getFirst()).contains("PRICE_TOO_LOW");
                        });
    }

    @Test
    void Redis_PubSub_경로로_로컬_세션에_메시지가_전달된다() {
        List<String> received = new CopyOnWriteArrayList<>();
        String sessionId = "ws-pubsub-1";
        WebSocketSession session = WebSocketTestSupport.mockSession(sessionId, received);

        sessionRegistry.registerAuctionSession("auction-pubsub", session);
        awaitRedisAuctionSession("auction-pubsub", sessionId);

        String message = "{\"type\":\"BID_UPDATED\",\"auctionId\":\"auction-pubsub\"}";
        redisSessionStore
                .publishNotify(redisSessionStore.getInstanceId(), sessionId, message)
                .block(Duration.ofSeconds(5));

        Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(received).containsExactly(message));
    }

    @Test
    void 원격_인스턴스_세션만_있으면_로컬_WebSocket에_직접_push하지_않는다() {
        String auctionId = "auction-remote-only";
        String remoteRef = "other-instance:ws-remote";

        reactiveRedis
                .opsForSet()
                .add("ws:auction:" + auctionId + ":sessions", remoteRef)
                .block(Duration.ofSeconds(5));

        // 무관한 로컬 세션을 감시용으로 등록해, 잘못된 push 여부를 실제로 검증
        List<String> received = new CopyOnWriteArrayList<>();
        WebSocketSession localUnusedSession =
                WebSocketTestSupport.mockSession("ws-local-unused", received);
        sessionRegistry.registerAuctionSession("auction-local-unused", localUnusedSession);
        awaitRedisAuctionSession("auction-local-unused", "ws-local-unused");

        sessionRegistry
                .sendToAuction(auctionId, "{\"type\":\"BID_UPDATED\"}")
                .block(Duration.ofSeconds(5));

        // 원격 타깃은 Pub/Sub으로만 전달되며, 이 JVM에 연결된 ws-local-unused 세션에는 보내지 않음
        assertThat(received).isEmpty();
    }

    private void awaitRedisUserSession(String userId) {
        Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .untilAsserted(
                        () ->
                                assertThat(
                                                redisSessionStore
                                                        .getUserSessionTarget(userId)
                                                        .block(Duration.ofSeconds(2)))
                                        .isNotNull());
    }

    private void awaitRedisAuctionSession(String auctionId, String sessionId) {
        Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .untilAsserted(
                        () ->
                                assertThat(
                                                redisSessionStore
                                                        .getAuctionSessionTargets(auctionId)
                                                        .collectList()
                                                        .block(Duration.ofSeconds(2)))
                                        .anyMatch(
                                                t ->
                                                        sessionId.equals(t.sessionId())
                                                                && redisSessionStore
                                                                        .getInstanceId()
                                                                        .equals(t.instanceId())));
    }
}
