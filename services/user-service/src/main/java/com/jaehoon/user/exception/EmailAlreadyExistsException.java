package com.jaehoon.user.exception;

public class EmailAlreadyExistsException extends RuntimeException {
    public EmailAlreadyExistsException(String email) {
        super("이미 사용 중인 이메일입니다: " + email);
    }
}
