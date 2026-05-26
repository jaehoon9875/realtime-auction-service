package com.jaehoon.notification.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.boot.test.json.JacksonTester;

import tools.jackson.databind.json.JsonMapper;

/**
 * Spring Boot 4 Jackson 3 auto-config(JsonMapper) 동작 검증.
 */
@JsonTest
class JsonMapperAutoConfigurationTest {

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private JacksonTester<Instant> instantJson;

    @Test
    void jsonMapper_빈이_등록된다() {
        assertThat(jsonMapper).isNotNull();
    }

    @Test
    void instant_은_ISO8601_문자열로_직렬화된다() throws Exception {
        Instant instant = Instant.parse("2026-05-06T01:00:00Z");

        assertThat(instantJson.write(instant).getJson()).isEqualTo("\"2026-05-06T01:00:00Z\"");
    }
}
