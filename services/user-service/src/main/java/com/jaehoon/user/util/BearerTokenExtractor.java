package com.jaehoon.user.util;

import org.springframework.util.StringUtils;

/**
 * Authorization 헤더에서 Bearer 토큰을 추출하는 유틸리티.
 * Filter와 Controller 양쪽에서 동일한 로직이 중복되지 않도록 공통화.
 */
public final class BearerTokenExtractor {

    private BearerTokenExtractor() {}

    /**
     * "Bearer {token}" 형식의 헤더에서 토큰 값만 반환.
     * 형식이 올바르지 않거나 토큰 부분이 비어 있으면 null 반환.
     */
    public static String extract(String header) {
        if (!StringUtils.hasText(header) || !header.startsWith("Bearer ")) {
            return null;
        }
        String token = header.substring(7).trim();
        return StringUtils.hasText(token) ? token : null;
    }
}
