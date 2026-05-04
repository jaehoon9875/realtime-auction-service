package com.jaehoon.auction.exception;

/**
 * auction-streams 등 외부 의존 서비스 장애 시 발생.
 * GlobalExceptionHandler 에서 503 으로 매핑된다.
 */
public class ExternalServiceException extends RuntimeException {

    public ExternalServiceException(String message) {
        super(message);
    }
}
