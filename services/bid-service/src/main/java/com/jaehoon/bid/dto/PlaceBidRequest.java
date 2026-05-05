package com.jaehoon.bid.dto;

import java.util.UUID;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record PlaceBidRequest(
        @NotNull(message = "auctionId는 필수입니다.")
        UUID auctionId,
        @NotNull(message = "amount는 필수입니다.")
        @Min(value = 1, message = "amount는 1 이상이어야 합니다.")
        Long amount) {
}
