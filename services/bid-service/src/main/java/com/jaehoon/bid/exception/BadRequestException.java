package com.jaehoon.bid.exception;

/**
 * 입찰 도메인 규칙 위반(상태, 마감 시각, 금액 조건) 시 사용하는 400 예외.
 */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
