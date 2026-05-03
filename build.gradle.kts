plugins {
    id("org.springframework.boot") version "4.0.6" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
}

subprojects {
    apply(plugin = "java")

    group = "com.jaehoon"
    version = "0.0.1-SNAPSHOT"

    the<JavaPluginExtension>().toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }

    repositories {
        mavenCentral()
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }

    // 모든 Spring Boot 서비스 모듈에 적용. 각 모듈 build.gradle.kts에는 spring-boot + dependency-management 플러그인 필요.
    // 루트 스크립트에는 Kotlin DSL의 implementation 등 접근자가 없어 add("구성명", "Maven 좌표") 형태로 선언한다.
    dependencies {
        // --- 운영 모니터링 (Actuator) ---
        add("implementation", "org.springframework.boot:spring-boot-starter-actuator")

        // --- JWT (jjwt-api: 인터페이스, impl/jackson: 런타임 구현체) ---
        add("implementation", "io.jsonwebtoken:jjwt-api:0.12.7")
        add("runtimeOnly", "io.jsonwebtoken:jjwt-impl:0.12.7")
        add("runtimeOnly", "io.jsonwebtoken:jjwt-jackson:0.12.7")

        // --- Lombok (컴파일 시 코드 생성, 런타임 불필요) ---
        add("compileOnly", "org.projectlombok:lombok")
        add("annotationProcessor", "org.projectlombok:lombok")

        // --- Test ---
        add("testImplementation", "org.springframework.boot:spring-boot-starter-test")
        add("testCompileOnly", "org.projectlombok:lombok")
        add("testAnnotationProcessor", "org.projectlombok:lombok")
        // Gradle test 태스크에서 JUnit Platform 사용 시
        add("testRuntimeOnly", "org.junit.platform:junit-platform-launcher")
    }
}
