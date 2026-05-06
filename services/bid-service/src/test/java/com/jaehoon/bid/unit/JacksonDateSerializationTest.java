package com.jaehoon.bid.unit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.boot.test.json.JacksonTester;

@JsonTest
class JacksonDateSerializationTest {

    @Autowired
    JacksonTester<Instant> json;

    @Test
    void instant_은_ISO8601_문자열로_직렬화된다() throws Exception {
        Instant instant = Instant.parse("2026-05-06T01:00:00Z");

        var result = json.write(instant);

        // 숫자 타임스탬프(1746493200.000000000)가 아닌 ISO-8601 문자열이어야 한다.
        assertThat(result.getJson()).isEqualTo("\"2026-05-06T01:00:00Z\"");
    }
}
