package com.jaehoon.auction.dto;

import com.jaehoon.auction.entity.AuctionStatus;
import jakarta.validation.constraints.NotNull;

/**
 * 경매 상태 변경 요청 DTO (내부 API 전용).
 * 허용 상태: ONGOING | CLOSED
 */
public record UpdateAuctionStatusRequest(

        @NotNull(message = "상태 값은 필수입니다")
        AuctionStatus status
) {
}
