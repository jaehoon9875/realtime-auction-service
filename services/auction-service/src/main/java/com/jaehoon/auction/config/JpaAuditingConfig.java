package com.jaehoon.auction.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * JPA Auditing (@CreatedDate / @LastModifiedDate) 활성화.
 * 메인 애플리케이션 클래스에 두지 않아 슬라이스 테스트(@WebMvcTest 등) 시 불필요한 로딩을 피한다.
 * {@code test} 프로파일은 JPA 자동 설정을 끄는 경량 스모크용이므로 Auditing을 켜지 않는다
 * (부트스트랩 순서상 {@code @ConditionalOnBean(EntityManagerFactory)}는 누락될 수 있어 프로파일로 구분).
 */
@Configuration
@Profile("!test")
@EnableJpaAuditing
public class JpaAuditingConfig {
}
