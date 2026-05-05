package com.jaehoon.auction.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.jaehoon.auction.entity.Auction;
import com.jaehoon.auction.entity.AuctionStatus;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.QueryHints;

public interface AuctionRepository extends JpaRepository<Auction, UUID> {

    /**
     * 경매 목록 API — 최근 생성 순(페이징).
     * 정렬·필터 요구가 늘면 Querydsl 또는 명시적 쿼리로 확장한다.
     */
    Page<Auction> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /**
     * 스케줄러가 동시에 같은 행을 전환할 때 경합을 줄이기 위해 배타 락으로 조회한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("SELECT a FROM Auction a WHERE a.id = :id")
    Optional<Auction> findByIdForUpdate(@Param("id") UUID id);

    /** 시작 시각이 도래한 예약 경매(PENDING) 후보 ID 목록 (소량 배치). */
    @Query("SELECT a.id FROM Auction a WHERE a.status = :status AND a.startsAt <= :now ORDER BY a.startsAt ASC")
    List<UUID> findIdsDuePendingAuctions(
            @Param("status") AuctionStatus status,
            @Param("now") LocalDateTime now,
            Pageable pageable);

    /** 마감 시각이 지난 진행 중(ONGOING) 경매 후보 ID 목록 (소량 배치). */
    @Query("SELECT a.id FROM Auction a WHERE a.status = :status AND a.endsAt <= :now ORDER BY a.endsAt ASC")
    List<UUID> findIdsOngoingPastEnd(
            @Param("status") AuctionStatus status,
            @Param("now") LocalDateTime now,
            Pageable pageable);
}
