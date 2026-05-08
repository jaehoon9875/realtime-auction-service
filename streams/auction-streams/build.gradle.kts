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
    testImplementation("org.springframework.kafka:spring-kafka-test") // Embedded Kafka 기반 테스트 유틸에 사용
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
