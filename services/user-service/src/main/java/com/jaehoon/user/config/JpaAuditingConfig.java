package com.jaehoon.user.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * JPA Auditing (@CreatedDate) 활성화.
 * 메인 애플리케이션 클래스에 두지 않아 슬라이스 테스트 시 불필요한 로딩을 피한다.
 * {@code test} 프로파일은 JPA를 끄는 경량 스모크용이면 Auditing을 켜지 않는다.
 */
@Configuration
@Profile("!test")
@EnableJpaAuditing
public class JpaAuditingConfig {
}
