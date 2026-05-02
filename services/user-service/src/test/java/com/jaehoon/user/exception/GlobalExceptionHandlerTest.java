package com.jaehoon.user.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    @DisplayName("PostgreSQL unique_violation(23505) → 400, 이메일 중복 메시지")
    void handleDataIntegrity_uniqueViolation() {
        SQLException sqlEx = new SQLException("duplicate key value violates unique constraint", "23505");
        DataIntegrityViolationException ex = new DataIntegrityViolationException("constraint failed", sqlEx);

        ResponseEntity<ErrorResponse> res = handler.handleDataIntegrity(ex);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(res.getBody()).isNotNull();
        assertThat(res.getBody().message()).isEqualTo("이미 사용 중인 이메일입니다");
    }

    @Test
    @DisplayName("다른 무결성 오류 → 409")
    void handleDataIntegrity_other() {
        SQLException sqlEx = new SQLException("foreign key violation", "23503");
        DataIntegrityViolationException ex = new DataIntegrityViolationException("fk failed", sqlEx);

        ResponseEntity<ErrorResponse> res = handler.handleDataIntegrity(ex);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(res.getBody()).isNotNull();
        assertThat(res.getBody().message()).isEqualTo("요청을 처리할 수 없습니다");
    }
}
