plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

dependencyManagement {
    imports {
        // Spring Boot 4.0.x + Spring Cloud Oakwood (프로퍼티 없이 고정 좌표로 먼저 해석되는지 확인)
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:2025.1.1")
    }
}

dependencies {
    // API Gateway 코어 (Spring Cloud Gateway)
    implementation("org.springframework.cloud:spring-cloud-starter-gateway-server-webflux")
    // 운영 모니터링 (Actuator)
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // JWT 검증 (Gateway GlobalFilter에서 사용)
    implementation("io.jsonwebtoken:jjwt-api:0.12.7")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.7")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.7")

    // Lombok (@ConfigurationProperties 바인딩용 getter/setter 등)
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
