plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

dependencyManagement {
    imports {
        // --- Spring Cloud (Oakwood BOM) ---
        // Spring Boot 4.0.x와 함께 쓰는 Gateway·Cloud Stack 버전 정렬
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:2025.1.1")
    }
}

dependencies {
    // --- Spring Cloud Gateway (API Gateway 코어) ---
    implementation("org.springframework.cloud:spring-cloud-starter-gateway-server-webflux")
    // Actuator, JWT, Lombok, spring-boot-starter-test 등은 루트 build.gradle.kts subprojects에서 공통 적용
}
