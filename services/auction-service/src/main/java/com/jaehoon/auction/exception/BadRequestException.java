package com.jaehoon.auction.exception;

/**
 * 잘못된 경매 입력(시작·마감 시각 조합 등) — HTTP 400.
 */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
