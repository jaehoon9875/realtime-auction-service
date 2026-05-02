package com.jaehoon.user.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BearerTokenExtractorTest {

    @Test
    @DisplayName("정상 Bearer 헤더에서 토큰 추출")
    void extract_정상() {
        String result = BearerTokenExtractor.extract("Bearer mytoken123");

        assertThat(result).isEqualTo("mytoken123");
    }

    @Test
    @DisplayName("null 헤더 → null 반환")
    void extract_null헤더() {
        assertThat(BearerTokenExtractor.extract(null)).isNull();
    }

    @Test
    @DisplayName("빈 문자열 헤더 → null 반환")
    void extract_빈문자열() {
        assertThat(BearerTokenExtractor.extract("")).isNull();
    }

    @Test
    @DisplayName("공백만 있는 헤더 → null 반환")
    void extract_공백만() {
        assertThat(BearerTokenExtractor.extract("   ")).isNull();
    }

    @Test
    @DisplayName("Bearer prefix 없는 헤더 → null 반환")
    void extract_Bearer없음() {
        assertThat(BearerTokenExtractor.extract("Basic dXNlcjpwYXNz")).isNull();
    }

    @Test
    @DisplayName("소문자 bearer → null 반환 (대소문자 구분)")
    void extract_소문자bearer() {
        assertThat(BearerTokenExtractor.extract("bearer mytoken123")).isNull();
    }

    @Test
    @DisplayName("Bearer만 있고 토큰 없음 → null 반환")
    void extract_Bearer뒤공백만() {
        // "Bearer " 뒤가 비어 있으면 유효한 토큰이 없다고 판단 → null
        assertThat(BearerTokenExtractor.extract("Bearer ")).isNull();
    }
}
