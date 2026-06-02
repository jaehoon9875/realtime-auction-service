package com.jaehoon.bid.service;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.jaehoon.bid.dto.BidResponse;
import com.jaehoon.bid.exception.AuctionNotFoundException;
import com.jaehoon.bid.exception.BadRequestException;
import com.jaehoon.bid.repository.BidRepository;
import com.jaehoon.bid.service.AuctionServiceClient.AuctionSnapshot;

import io.micrometer.context.ContextSnapshotFactory;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BidService {

    private static final String AUCTION_STATUS_ONGOING = "ONGOING";

    private final BidRepository bidRepository;
    private final AuctionServiceClient auctionServiceClient;
    private final AuctionStreamsClient auctionStreamsClient;
    private final BidTransactionService bidTransactionService;
    private final ExecutorService bidLookupExecutor;
    private final ContextSnapshotFactory contextSnapshotFactory;

    // 경매/입찰 유효성 검증 후 입찰 저장+Outbox 저장을 위임한다 (검증은 트랜잭션 밖에서 수행).
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public BidResponse placeBid(UUID bidderId, UUID auctionId, Long amount) {
        // 서로 독립적인 외부 조회를 Virtual Thread에서 동시에 시작해 입찰 핫패스의 직렬 RTT를 줄인다.
        // 요청 스레드의 트레이싱/MDC 컨텍스트를 스냅샷으로 떠 조회 스레드에 전파한다(분산 추적 span 연결 유지).
        Executor tracedExecutor = contextSnapshotFactory.captureAll().wrapExecutor(bidLookupExecutor);
        CompletableFuture<AuctionSnapshot> auctionFuture = CompletableFuture.supplyAsync(
                () -> auctionServiceClient.getAuction(auctionId),
                tracedExecutor);
        CompletableFuture<Long> currentPriceFuture = CompletableFuture.supplyAsync(
                () -> auctionStreamsClient.getCurrentPrice(auctionId),
                tracedExecutor);

        AuctionSnapshot auction;
        try {
            auction = join(auctionFuture);
            if (auction == null) {
                throw new AuctionNotFoundException(auctionId);
            }
            validateAuctionOpen(auction);
        } catch (RuntimeException e) {
            // 앞 단계에서 입찰이 거절되면 최고가 조회 결과는 더 필요 없으므로 버린다.
            // 단, 이미 시작된 HTTP 호출 자체는 인터럽트되지 않고 완료된다(CompletableFuture.cancel 한계).
            currentPriceFuture.cancel(true);
            throw e;
        }
        validateBidAmount(auction, amount, join(currentPriceFuture));

        return bidTransactionService.saveBidWithOutbox(bidderId, auctionId, amount);
    }

    // 내 입찰 목록을 최신순(placedAt desc)으로 조회해 API 응답 DTO로 변환한다.
    public Page<BidResponse> getMyBids(UUID bidderId, Pageable pageable) {
        return bidRepository.findByBidderIdOrderByPlacedAtDesc(bidderId, pageable)
                .map(BidResponse::from);
    }

    // 경매가 진행 중인지와 마감 여부를 검사해, 입찰 가능 시간대인지 판단한다.
    private void validateAuctionOpen(AuctionSnapshot auction) {
        if (!AUCTION_STATUS_ONGOING.equals(auction.status())) {
            throw new BadRequestException("진행 중인 경매가 아닙니다.");
        }
        // 마감 시각 비교는 절대 시각(Instant) 기준으로 판단한다.
        if (!auction.endsAt().isAfter(Instant.now())) {
            throw new BadRequestException("마감된 경매입니다.");
        }
    }

    // 첫 입찰(null currentPrice)은 시작가와 비교하고, 이후 입찰은 현재 최고가보다 큰지 검증한다.
    private void validateBidAmount(AuctionSnapshot auction, Long amount, Long currentPrice) {
        if (currentPrice == null) {
            if (amount <= auction.startPrice()) {
                throw new BadRequestException("시작가보다 높아야 합니다.");
            }
            return;
        }
        if (amount <= currentPrice) {
            throw new BadRequestException("현재 최고가보다 높아야 합니다.");
        }
    }

    // CompletableFuture가 감싼 기존 런타임 예외를 그대로 전달해 기존 API 예외 매핑을 유지한다.
    private <T> T join(CompletableFuture<T> future) {
        try {
            return future.join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw e;
        }
    }
}
