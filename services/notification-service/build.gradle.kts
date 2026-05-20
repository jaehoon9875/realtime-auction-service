import com.github.davidmc24.gradle.plugin.avro.GenerateAvroJavaTask

plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    id("com.github.davidmc24.gradle.plugin.avro-base") version "1.9.1"
}

repositories {
    mavenCentral()
    maven("https://packages.confluent.io/maven/")
}

val confluentVersion = "8.2.0"

dependencies {
    // --- Spring Boot Starters ---
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")

    // --- Kafka ---
    implementation("org.springframework.kafka:spring-kafka")

    // --- Redis (세션 공유 + Pub/Sub) ---
    implementation("org.springframework.boot:spring-boot-starter-data-redis-reactive")

    // --- Avro + Schema Registry ---
    implementation("org.apache.avro:avro:1.12.0")
    implementation("io.confluent:kafka-avro-serializer:$confluentVersion")

    // --- Test ---
    // spring-boot-starter-test, Lombok(테스트), JUnit Platform 런처는 루트 build.gradle.kts subprojects에서 공통 적용
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.springframework.kafka:spring-kafka-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-kafka")
    testImplementation("com.redis:testcontainers-redis")
    testImplementation("org.awaitility:awaitility:4.2.2")
    testImplementation("io.projectreactor:reactor-test")
}

val generateAvro = tasks.register<GenerateAvroJavaTask>("generateAvro") {
    source(fileTree(layout.projectDirectory.dir("../../infra/avro")) {
        include("*.avsc")
    })
    setOutputDir(layout.buildDirectory.dir("generated-main-avro-java").get().asFile)
}

sourceSets {
    main {
        java {
            srcDir(generateAvro)
        }
    }
}

tasks.named<JavaCompile>("compileJava") {
    source(generateAvro)
}

tasks.named<Test>("test") {
    useJUnitPlatform {
        // Docker(Testcontainers) 없이도 CI·로컬 기본 test 통과. 통합 테스트는 integrationTest 태스크 사용.
        excludeTags("integration")
    }
}

tasks.register<Test>("integrationTest") {
    group = "verification"
    description = "Testcontainers(Kafka, Redis) 통합 테스트 — Docker 필요"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform {
        includeTags("integration")
    }
    shouldRunAfter(tasks.named("test"))
}
