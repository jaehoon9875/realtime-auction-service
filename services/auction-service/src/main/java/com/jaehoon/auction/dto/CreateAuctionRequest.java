package com.jaehoon.auction.dto;

import java.time.LocalDateTime;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 경매 생성 요청 DTO.
 * description 은 선택 입력이며 null 허용 (DB 스키마와 동일).
 */
public record CreateAuctionRequest(

        @NotBlank(message = "제목은 필수입니다")
        @Size(max = 255, message = "제목은 255자 이내여야 합니다")
        String title,

        String description,

        @NotNull(message = "시작가는 필수입니다")
        @Min(value = 1, message = "시작가는 1원 이상이어야 합니다")
        Long startPrice,

        @NotNull(message = "마감 시각은 필수입니다")
        @Future(message = "마감 시각은 현재 시각 이후여야 합니다")
        LocalDateTime endsAt
) {
}
