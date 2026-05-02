plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

dependencies {
    // --- Spring Boot Starters ---
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")

    // --- JWT (jjwt-api: 인터페이스, impl/jackson: 런타임 구현체) ---
    implementation("io.jsonwebtoken:jjwt-api:0.12.7")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.7")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.7")

    // --- Database (Flyway: 마이그레이션, postgresql: JDBC 드라이버) ---
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    // --- Lombok (컴파일 시 코드 생성, 런타임 불필요) ---
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    // --- Test ---
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    // Spring Boot 4: @WebMvcTest / @AutoConfigureMockMvc 등 MVC 테스트 슬라이스 (starter-test에 미포함)
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    // Spring Security 테스트 (@WithMockUser, SecurityMockMvcRequestPostProcessors)
    testImplementation("org.springframework.security:spring-security-test")
    // Testcontainers (통합 테스트용 PostgreSQL + Redis 실제 컨테이너 기동)
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    // Testcontainers 2.x: 아티팩트 ID가 testcontainers-* 접두사로 변경됨 (BOM 버전은 Spring Boot가 가져옴)
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testCompileOnly("org.projectlombok:lombok")
    testAnnotationProcessor("org.projectlombok:lombok")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
