package com.jaehoon.bid.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class InternalRequestTokenFilterTest {

    private final InternalRequestTokenFilter filter = new InternalRequestTokenFilter(
            new BidSecurityProperties("X-Internal-Request-Token", "secret"),
            new MockEnvironment());

    @Test
    @DisplayName("모니터링 요청은 내부 토큰 없이 통과한다")
    void monitoringEndpoint_내부토큰없이통과() throws Exception {
        assertMonitoringEndpointPasses("/actuator/prometheus");
        assertMonitoringEndpointPasses("/actuator/health");
        assertMonitoringEndpointPasses("/actuator/health/readiness");
    }

    @Test
    @DisplayName("context path가 있어도 모니터링 요청은 내부 토큰 없이 통과한다")
    void monitoringEndpoint_contextPath가있어도내부토큰없이통과() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/actuator/prometheus");
        request.setContextPath("/internal");
        request.setServletPath("/actuator/prometheus");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(filterChain.getRequest()).isNotNull();
    }

    @Test
    @DisplayName("일반 요청은 내부 토큰이 없으면 차단한다")
    void generalEndpoint_내부토큰없으면차단() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/bids");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(filterChain.getRequest()).isNull();
    }

    private void assertMonitoringEndpointPasses(String path) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setServletPath(path);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(filterChain.getRequest()).isNotNull();
    }
}
