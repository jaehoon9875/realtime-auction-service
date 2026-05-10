import com.github.davidmc24.gradle.plugin.avro.GenerateAvroJavaTask

// Avro: .avsc→Java는 davidmc24 avro-base 1.9.1(Avro 1.11.3 기준). Java 21·Gradle 9.4.1에서 검증, Apache 공식 플러그인 없음·유사 대안도 비공식이라 유지.
// 런타임은 아래 org.apache.avro 1.12.0.

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
    implementation("org.springframework.boot:spring-boot-starter-webmvc") // Interactive Query REST API 엔드포인트 제공에 사용
    implementation("org.springframework.boot:spring-boot-starter-actuator") // 헬스체크/메트릭 노출에 사용

    // --- Kafka Streams ---
    implementation("org.springframework.kafka:spring-kafka") // Kafka 프로듀서/컨슈머 공통 설정 및 통합에 사용
    implementation("org.apache.kafka:kafka-streams") // Streams 토폴로지/State Store 처리에 사용

    // --- Confluent Avro / Schema Registry ---
    implementation("org.apache.avro:avro:1.12.0") // infra/avro 스키마 기반 Java 클래스 생성 결과 컴파일에 사용
    implementation("io.confluent:kafka-avro-serializer:$confluentVersion") // Avro 직렬화/역직렬화에 사용
    implementation("io.confluent:kafka-streams-avro-serde:$confluentVersion") // Kafka Streams용 Avro Serde에 사용
    implementation("io.confluent:kafka-schema-registry-client:$confluentVersion") // Schema Registry 연동에 사용

    // --- Test ---
    testImplementation("org.springframework.boot:spring-boot-starter-test") // JUnit5 + Mockito + AssertJ
    testImplementation("org.springframework.kafka:spring-kafka-test") // Embedded Kafka 기반 테스트 유틸에 사용
    testImplementation("org.apache.kafka:kafka-streams-test-utils") // TopologyTestDriver 제공
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
