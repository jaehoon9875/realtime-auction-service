plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

dependencyManagement {
    imports {
        // Spring Cloud Oakwood BOM — Resilience4j Circuit Breaker 등 좌표 버전 정렬 (api-gateway와 동일)
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:2025.1.1")
    }
}

dependencies {
    // --- Spring Boot Starters ---
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.cloud:spring-cloud-starter-circuitbreaker-resilience4j")

    // --- Database (Flyway: 마이그레이션, postgresql: JDBC 드라이버) ---
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    // --- Test ---
    // spring-boot-starter-test, Lombok(테스트), JUnit Platform 런처는 루트 build.gradle.kts subprojects에서 공통 적용
    // Spring Boot 4: @WebMvcTest / @AutoConfigureMockMvc 등 MVC 테스트 슬라이스 (starter-test에 미포함)
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    // Spring Security 테스트 (@WithMockUser, SecurityMockMvcRequestPostProcessors)
    testImplementation("org.springframework.security:spring-security-test")
    // Testcontainers (통합 테스트용 PostgreSQL 등 실제 컨테이너 기동)
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    // Testcontainers 2.x: 아티팩트 ID가 testcontainers-* 접두사로 변경됨 (BOM 버전은 Spring Boot가 가져옴)
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
}
