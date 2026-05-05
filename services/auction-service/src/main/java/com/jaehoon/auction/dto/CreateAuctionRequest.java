package com.jaehoon.auction.dto;

import java.time.LocalDateTime;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 경매 생성 요청 DTO.
 * description 은 선택 입력이며 null 허용 (DB 스키마와 동일).
 * startsAt 은 생략 시 서버가 생성 시각으로 채운다.
 */
public record CreateAuctionRequest(

        @NotBlank(message = "제목은 필수입니다")
        @Size(max = 255, message = "제목은 255자 이내여야 합니다")
        String title,

        String description,

        @NotNull(message = "시작가는 필수입니다")
        @Min(value = 1, message = "시작가는 1원 이상이어야 합니다")
        Long startPrice,

        /**
         * 경매 시작 시각. null 이면 서버에서 요청 처리 시각을 사용한다.
         */
        @Nullable
        LocalDateTime startsAt,

        @NotNull(message = "마감 시각은 필수입니다")
        @Future(message = "마감 시각은 현재 시각 이후여야 합니다")
        LocalDateTime endsAt
) {
}
