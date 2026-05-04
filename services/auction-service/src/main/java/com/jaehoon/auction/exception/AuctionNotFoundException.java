package com.jaehoon.auction.exception;

import java.util.UUID;

public class AuctionNotFoundException extends RuntimeException {

    public AuctionNotFoundException(UUID auctionId) {
        super("경매를 찾을 수 없습니다: " + auctionId);
    }
}
