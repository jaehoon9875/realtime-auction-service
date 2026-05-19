package com.jaehoon.user.support;

import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * MockMvc 요청 보조 유틸.
 */
public final class MockMvcRequestSupport {

    private MockMvcRequestSupport() {
    }

    /**
     * SecurityConfig의 bearerTokenResolver가 {@code getServletPath()}로 refresh 경로를 판별하므로,
     * MockMvc 기본 요청에 servletPath를 맞춘다.
     */
    public static RequestPostProcessor servletPath(String path) {
        return request -> {
            if (request instanceof MockHttpServletRequest mock) {
                mock.setServletPath(path);
            }
            return request;
        };
    }
}
