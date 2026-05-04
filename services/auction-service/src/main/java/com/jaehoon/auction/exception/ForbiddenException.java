package com.jaehoon.auction.exception;

/**
 * 판매자 본인이 아닌 사용자가 경매를 수정하려 할 때 발생한다.
 * 기존 오타 파일(ForbiddenExeption.java)은 삭제해도 된다.
 */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException() {
        super("해당 경매에 대한 권한이 없습니다");
    }
}
