package com.jaehoon.bid.exception;

/**
 * auction-service, auction-streams 같은 외부 의존 서비스 장애를 503으로 매핑하기 위한 예외.
 */
public class ExternalServiceException extends RuntimeException {

    public ExternalServiceException(String message) {
        super(message);
    }
}
