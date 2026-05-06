package com.jaehoon.bid.entity;

/**
 * bids.status 컬럼과 1:1 매핑되는 입찰 처리 결과.
 * - ACCEPTED: 유효성 검증 통과 후 수락된 입찰
 * - REJECTED: 규칙 위반으로 거절된 입찰(현재는 주로 400 즉시 응답, 향후 감사/이력 용도 확장 여지)
 */
public enum BidStatus {
    ACCEPTED,
    REJECTED
}
