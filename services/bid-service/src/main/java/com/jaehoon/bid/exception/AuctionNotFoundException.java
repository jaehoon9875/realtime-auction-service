package com.jaehoon.bid.exception;

import java.util.UUID;

/**
 * 입찰 대상 경매가 존재하지 않을 때 사용하는 404 예외.
 */
public class AuctionNotFoundException extends RuntimeException {

    public AuctionNotFoundException(UUID auctionId) {
        super("경매를 찾을 수 없습니다: " + auctionId);
    }
}
