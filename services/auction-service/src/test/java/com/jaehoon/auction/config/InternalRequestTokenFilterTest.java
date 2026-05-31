package com.jaehoon.auction.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class InternalRequestTokenFilterTest {

    private final InternalRequestTokenFilter filter = new InternalRequestTokenFilter(
            new AuctionSecurityProperties("X-Internal-Request-Token", "secret", List.of()),
            new MockEnvironment());

    @Test
    @DisplayName("Prometheus scrape 요청은 내부 토큰 없이 통과한다")
    void prometheusEndpoint_내부토큰없이통과() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/prometheus");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(filterChain.getRequest()).isNotNull();
    }

    @Test
    @DisplayName("일반 요청은 내부 토큰이 없으면 차단한다")
    void generalEndpoint_내부토큰없으면차단() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/auctions");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(filterChain.getRequest()).isNull();
    }
}
