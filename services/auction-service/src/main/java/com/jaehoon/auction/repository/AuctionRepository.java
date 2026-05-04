package com.jaehoon.auction.repository;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.jaehoon.auction.entity.Auction;

public interface AuctionRepository extends JpaRepository<Auction, UUID> {

    /**
     * 경매 목록 API — 최근 생성 순(페이징).
     * 정렬·필터 요구가 늘면 Querydsl 또는 명시적 쿼리로 확장한다.
     */
    Page<Auction> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
