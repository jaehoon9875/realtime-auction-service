package com.jaehoon.auction.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.jaehoon.auction.dto.AuctionResponse;
import com.jaehoon.auction.dto.CreateAuctionRequest;
import com.jaehoon.auction.entity.Auction;
import com.jaehoon.auction.entity.AuctionStatus;
import com.jaehoon.auction.entity.OutboxEvent;
import com.jaehoon.auction.exception.AuctionNotFoundException;
import com.jaehoon.auction.exception.ForbiddenException;
import com.jaehoon.auction.repository.AuctionRepository;
import com.jaehoon.auction.repository.OutboxEventRepository;
import com.jaehoon.auction.service.AuctionService;
import com.jaehoon.auction.service.AuctionStreamsClient;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;

/**
 * auction-service 서비스 레이어 통합 테스트.
 * - Testcontainers PostgreSQL + Flyway 마이그레이션으로 실제 DB 사용
 * - AuctionStreamsClient는 외부 의존성이므로 @MockitoBean으로 대체
 * - 각 테스트 시작 전 repository 정리로 테스트 간 격리 보장
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
// CI에서 SPRING_PROFILES_ACTIVE=test 가 주입되어 JPA가 제외되는 것을 방지
@ActiveProfiles("integration")
class AuctionIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    // auction-streams는 통합 테스트 범위 밖이므로 Mock으로 대체
    @MockitoBean
    AuctionStreamsClient auctionStreamsClient;

    @Autowired
    AuctionService auctionService;

    @Autowired
    AuctionRepository auctionRepository;

    @Autowired
    OutboxEventRepository outboxEventRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        // 클래스 레벨 @Transactional 없이도 테스트 간 격리를 보장하기 위해 매 테스트 시작 전 정리
        outboxEventRepository.deleteAll();
        auctionRepository.deleteAll();
    }

    // ─────────────────────────── createAuction ───────────────────────────

    @Test
    void createAuction_auctions_테이블에_레코드가_생성된다() {
        // given — 예약 시작(미래 startsAt)이면 PENDING
        UUID sellerId = UUID.randomUUID();
        CreateAuctionRequest request = new CreateAuctionRequest(
                "통합테스트 경매", "설명", 5_000L,
                Instant.now().plus(1, ChronoUnit.HOURS),
                Instant.now().plus(1, ChronoUnit.DAYS));

        // when
        AuctionResponse response = auctionService.createAuction(request, sellerId);

        // then
        assertThat(auctionRepository.findById(response.id())).isPresent();
        assertThat(response.sellerId()).isEqualTo(sellerId);
        assertThat(response.status()).isEqualTo(AuctionStatus.PENDING);
    }

    @Test
    void createAuction_outbox_events_테이블에_레코드가_생성된다() {
        // given
        UUID sellerId = UUID.randomUUID();
        CreateAuctionRequest request = new CreateAuctionRequest(
                "아웃박스 검증", null, 1_000L, null, Instant.now().plus(1, ChronoUnit.DAYS));

        // when
        AuctionResponse response = auctionService.createAuction(request, sellerId);

        // then — Debezium이 읽을 outbox_events 레코드 검증
        List<OutboxEvent> events = outboxEventRepository.findAll();
        assertThat(events).hasSize(1);

        OutboxEvent event = events.get(0);
        assertThat(event.getAggregateType()).isEqualTo("AUCTION");
        assertThat(event.getAggregateId()).isEqualTo(response.id());
        assertThat(event.getEventType()).isEqualTo("AUCTION_CREATED");
        assertThat(event.getPayload()).containsKey("auctionId");
        assertThat(event.getPayload()).containsKey("sellerId");
        assertThat(event.getPayload()).containsKey("status");
        assertThat(event.getPayload()).containsKey("startsAt");
        assertThat(event.getCreatedAt()).isNotNull();
    }

    @Test
    void createAuction_auction과_outbox_이벤트가_같은_트랜잭션에_저장된다() {
        // given — 원자성 검증: auction INSERT + outbox INSERT가 항상 쌍으로 존재해야 함
        UUID sellerId = UUID.randomUUID();
        CreateAuctionRequest request = new CreateAuctionRequest(
                "원자성 검증", null, 2_000L, null, Instant.now().plus(1, ChronoUnit.DAYS));

        // when
        AuctionResponse response = auctionService.createAuction(request, sellerId);

        // then — 경매와 아웃박스 레코드가 동시에 존재
        assertThat(auctionRepository.findById(response.id())).isPresent();
        assertThat(outboxEventRepository.findAll())
                .extracting(OutboxEvent::getAggregateId)
                .containsExactly(response.id());
    }

    @Test
    void activateDueAuctions_시작시각_도래_후_PENDING이_ONGOING으로_바뀐다() {
        UUID sellerId = UUID.randomUUID();
        Instant end = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant start = Instant.now().plus(1, ChronoUnit.HOURS);
        AuctionResponse created = auctionService.createAuction(
                new CreateAuctionRequest("스케줄 검증", null, 1_000L, start, end),
                sellerId);
        assertThat(created.status()).isEqualTo(AuctionStatus.PENDING);

        int updatedStart = jdbcTemplate.update(
                "UPDATE auctions SET starts_at = ? WHERE id = ?",
                Timestamp.from(Instant.now().minus(1, ChronoUnit.MINUTES)),
                created.id());
        assertThat(updatedStart).isEqualTo(1);

        auctionService.activateDueAuctions();

        assertThat(auctionRepository.findById(created.id()))
                .isPresent()
                .get()
                .extracting(Auction::getStatus)
                .isEqualTo(AuctionStatus.ONGOING);
        assertThat(outboxEventRepository.findAll())
                .extracting(OutboxEvent::getEventType)
                .containsExactlyInAnyOrder("AUCTION_CREATED", "AUCTION_STATUS_CHANGED");
    }

    @Test
    void closeOverdueAuctions_endsAt_경과_후_ONGOING이_CLOSED로_바뀐다() {
        UUID sellerId = UUID.randomUUID();
        AuctionResponse created = auctionService.createAuction(
                new CreateAuctionRequest("마감 스케줄", null, 1_000L, null, Instant.now().plus(1, ChronoUnit.DAYS)),
                sellerId);
        assertThat(created.status()).isEqualTo(AuctionStatus.ONGOING);

        int updatedEnd = jdbcTemplate.update(
                "UPDATE auctions SET ends_at = ? WHERE id = ?",
                Timestamp.from(Instant.now().minus(1, ChronoUnit.MINUTES)),
                created.id());
        assertThat(updatedEnd).isEqualTo(1);

        auctionService.closeOverdueAuctions();

        assertThat(auctionRepository.findById(created.id()))
                .isPresent()
                .get()
                .extracting(Auction::getStatus)
                .isEqualTo(AuctionStatus.CLOSED);
        assertThat(outboxEventRepository.findAll())
                .extracting(OutboxEvent::getEventType)
                .containsExactlyInAnyOrder("AUCTION_CREATED", "AUCTION_STATUS_CHANGED");
    }

    // ─────────────────────────── updateStatus ───────────────────────────

    @Test
    void updateStatus_outbox_events_테이블에_상태변경_이벤트가_생성된다() {
        // given
        UUID sellerId = UUID.randomUUID();
        AuctionResponse created = auctionService.createAuction(
                new CreateAuctionRequest("상태변경 경매", null, 1_000L,
                        Instant.now().plus(1, ChronoUnit.HOURS),
                        Instant.now().plus(1, ChronoUnit.DAYS)),
                sellerId);

        // when
        auctionService.updateStatus(created.id(), AuctionStatus.ONGOING, sellerId);

        // then — AUCTION_CREATED + AUCTION_STATUS_CHANGED 두 건
        List<OutboxEvent> events = outboxEventRepository.findAll();
        assertThat(events).hasSize(2);
        assertThat(events)
                .extracting(OutboxEvent::getEventType)
                .containsExactlyInAnyOrder("AUCTION_CREATED", "AUCTION_STATUS_CHANGED");
    }

    @Test
    void updateStatus_DB에_변경된_상태가_반영된다() {
        // given
        UUID sellerId = UUID.randomUUID();
        AuctionResponse created = auctionService.createAuction(
                new CreateAuctionRequest("ONGOING 전환 테스트", null, 1_000L,
                        Instant.now().plus(1, ChronoUnit.HOURS),
                        Instant.now().plus(1, ChronoUnit.DAYS)),
                sellerId);

        // when
        auctionService.updateStatus(created.id(), AuctionStatus.ONGOING, sellerId);

        // then — DB에서 직접 조회하여 상태 변경 확인
        assertThat(auctionRepository.findById(created.id()))
                .isPresent()
                .get()
                .extracting(a -> a.getStatus())
                .isEqualTo(AuctionStatus.ONGOING);
    }

    @Test
    void updateStatus_판매자가_아니면_outbox_이벤트가_생성되지_않는다() {
        // given
        UUID sellerId = UUID.randomUUID();
        UUID otherId = UUID.randomUUID();
        AuctionResponse created = auctionService.createAuction(
                new CreateAuctionRequest("권한 검증", null, 1_000L,
                        Instant.now().plus(1, ChronoUnit.HOURS),
                        Instant.now().plus(1, ChronoUnit.DAYS)),
                sellerId);

        long outboxCountBefore = outboxEventRepository.count();

        // when & then
        assertThatThrownBy(() -> auctionService.updateStatus(created.id(), AuctionStatus.ONGOING, otherId))
                .isInstanceOf(ForbiddenException.class);

        // 예외 발생 후 아웃박스 이벤트 수가 증가하지 않아야 함
        assertThat(outboxEventRepository.count()).isEqualTo(outboxCountBefore);
    }

    // ─────────────────────────── 트랜잭션 롤백 격리 ───────────────────────────

    @Test
    void 각_테스트는_독립적으로_빈_DB에서_시작한다_검증_A() {
        // @BeforeEach 정리로 다른 테스트 데이터가 남아있지 않아야 함
        assertThat(auctionRepository.count()).isZero();
        assertThat(outboxEventRepository.count()).isZero();
    }

    @Test
    void 각_테스트는_독립적으로_빈_DB에서_시작한다_검증_B() {
        // 위 테스트(_A) 실행 여부와 무관하게, cleanDatabase() 이후 이 테스트는 비어 있어야 함
        auctionService.createAuction(
                new CreateAuctionRequest("격리 검증용", null, 1_000L, null, Instant.now().plus(1, ChronoUnit.DAYS)),
                UUID.randomUUID());

        assertThat(auctionRepository.count()).isEqualTo(1);
        assertThat(outboxEventRepository.count()).isEqualTo(1);
    }

    // ─────────────────────────── 예외 시나리오 ───────────────────────────

    @Test
    void getAuction_존재하지_않는_경매_ID는_AuctionNotFoundException을_던진다() {
        // given
        UUID nonExistentId = UUID.randomUUID();

        // when & then
        assertThatThrownBy(() -> auctionService.getAuction(nonExistentId))
                .isInstanceOf(AuctionNotFoundException.class);
    }
}
