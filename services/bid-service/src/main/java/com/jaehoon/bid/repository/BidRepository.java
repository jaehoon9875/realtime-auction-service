package com.jaehoon.bid.repository;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.jaehoon.bid.entity.Bid;

public interface BidRepository extends JpaRepository<Bid, UUID> {

    /** 내 입찰 목록 API용: 최신 입찰 시각(placedAt) 기준 내림차순 페이징 */
    Page<Bid> findByBidderIdOrderByPlacedAtDesc(UUID bidderId, Pageable pageable);
}
