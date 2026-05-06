package com.jaehoon.bid.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "bids")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Bid {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "auction_id", nullable = false)
    private UUID auctionId;

    @Column(name = "bidder_id", nullable = false)
    private UUID bidderId;

    @Column(nullable = false)
    // docs/schema.md 기준: 금액은 원 단위 BIGINT로 관리하여 부동소수점 오차를 방지한다.
    private Long amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BidStatus status;

    @Column(name = "placed_at", nullable = false, updatable = false)
    private Instant placedAt;

    @Builder
    public Bid(UUID auctionId, UUID bidderId, Long amount, BidStatus status) {
        this.auctionId = auctionId;
        this.bidderId = bidderId;
        this.amount = amount;
        this.status = status;
    }

    // DB 기본값(now, ACCEPTED)과 동일한 규칙을 애플리케이션 레벨에서도 명시적으로 보장한다.
    @PrePersist
    private void prePersist() {
        this.placedAt = Instant.now();
        if (this.status == null) {
            this.status = BidStatus.ACCEPTED;
        }
    }
}
