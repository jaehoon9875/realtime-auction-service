package com.jaehoon.auction.entity;

/**
 * 경매 상태 머신.
 * 허용 전이: PENDING → ONGOING → CLOSED
 */
public enum AuctionStatus {

    PENDING,
    ONGOING,
    CLOSED;

    /** 현재 상태에서 next 로의 전이가 유효한지 반환한다. */
    public boolean canTransitionTo(AuctionStatus next) {
        return switch (this) {
            case PENDING -> next == ONGOING;
            case ONGOING  -> next == CLOSED;
            case CLOSED  -> false;
        };
    }
}
