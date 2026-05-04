package com.jaehoon.auction.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 경매 상태 변경 요청 DTO (내부 API 전용).
 * 허용 상태: ONGOING | CLOSED | CANCELLED
 */
public record UpdateAuctionStatusRequest(

        @NotBlank(message = "상태 값은 필수입니다")
        @Pattern(
                regexp = "ONGOING|CLOSED|CANCELLED",
                message = "상태는 ONGOING, CLOSED, CANCELLED 중 하나여야 합니다"
        )
        String status
) {
}
