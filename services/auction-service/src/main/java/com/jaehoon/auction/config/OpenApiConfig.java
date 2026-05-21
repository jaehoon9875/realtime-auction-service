package com.jaehoon.auction.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Auction Service OpenAPI 문서 및 Bearer 인증 스키마를 구성한다.
 */
@Configuration
public class OpenApiConfig {

    /**
     * Swagger UI용 OpenAPI 메타데이터와 보안 스키마를 생성한다.
     *
     * @return Auction Service용 OpenAPI 설정
     */
    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Auction Service API")
                        .version("0.7.0")
                        .description("경매 생성·조회·상태 관리"))
                .components(new Components()
                        .addSecuritySchemes("BearerAuth", new SecurityScheme()
                                .name("BearerAuth")
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
