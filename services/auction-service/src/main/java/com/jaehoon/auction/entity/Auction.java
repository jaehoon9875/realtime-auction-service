package com.jaehoon.auction.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "auctions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Auction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "seller_id", nullable = false)
    private UUID sellerId;

    @Column(nullable = false)
    private String title;

    /** DB 스키마상 NOT NULL 제약 없음 — 미입력 시 null */
    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "start_price", nullable = false)
    private Long startPrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuctionStatus status;

    @Column(name = "ends_at", nullable = false)
    private LocalDateTime endsAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * 신규 경매 생성용. id·타임스탬프·기본 status는 {@link #prePersist()}에서 채운다.
     */
    @Builder
    public Auction(UUID sellerId, String title, String description, Long startPrice, LocalDateTime endsAt,
            AuctionStatus status) {
        this.sellerId = sellerId;
        this.title = title;
        this.description = description;
        this.startPrice = startPrice;
        this.endsAt = endsAt;
        this.status = status;
    }

    // 최초 저장 시 타임스탬프 및 DB 기본값과 동일하게 status=PENDING
    @PrePersist
    private void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.status == null) {
            this.status = AuctionStatus.PENDING;
        }
    }

    // 엔티티 수정 시 갱신 시각 반영
    @PreUpdate
    private void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 경매 상태 전이. 허용되지 않는 전이는 즉시 예외를 던진다.
     * 유효한 전이: PENDING → ACTIVE → CLOSED
     */
    public void changeStatus(AuctionStatus newStatus) {
        if (!this.status.canTransitionTo(newStatus)) {
            throw new IllegalStateException(
                    "허용되지 않는 상태 전이: " + this.status + " → " + newStatus);
        }
        this.status = newStatus;
    }
}
