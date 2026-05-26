package com.jaehoon.auction.unit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.boot.test.json.JacksonTester;

/**
 * Spring Boot 4 Jackson 3 auto-config(JsonMapper) 동작 검증.
 */
@JsonTest
class JacksonDateSerializationTest {

    @Autowired
    private JacksonTester<Instant> json;

    @Test
    void instant_은_ISO8601_문자열로_직렬화된다() throws Exception {
        Instant instant = Instant.parse("2026-05-06T01:00:00Z");

        // 숫자 타임스탬프가 아닌 ISO-8601 문자열이어야 한다.
        assertThat(json.write(instant).getJson()).isEqualTo("\"2026-05-06T01:00:00Z\"");
    }
}
