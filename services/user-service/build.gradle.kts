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
    implementation("io.jsonwebtoken:jjwt-api:0.12.8")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.8")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.8")

    // --- Database (Flyway: 마이그레이션, postgresql: JDBC 드라이버) ---
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    // --- Lombok (컴파일 시 코드 생성, 런타임 불필요) ---
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    // --- Test ---
    testImplementation("org.springframework.boot:spring-boot-starter-actuator-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testCompileOnly("org.projectlombok:lombok")
    testAnnotationProcessor("org.projectlombok:lombok")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
