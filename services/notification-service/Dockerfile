# syntax=docker/dockerfile:1
# 빌드 컨텍스트: 저장소 루트 (infra/docker-compose.yml의 build.context: ..)
# Avro 생성(infra/avro) 포함 — notification-service/build.gradle.kts의 generateAvro 태스크

FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace

COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle/ gradle/

# settings.gradle.kts에 등록된 모듈 — Gradle 구성 단계용 build 스크립트
COPY services/api-gateway/build.gradle.kts services/api-gateway/build.gradle.kts
COPY services/user-service/build.gradle.kts services/user-service/build.gradle.kts
COPY services/auction-service/build.gradle.kts services/auction-service/build.gradle.kts
COPY services/bid-service/build.gradle.kts services/bid-service/build.gradle.kts
COPY streams/auction-streams/build.gradle.kts streams/auction-streams/build.gradle.kts

COPY services/notification-service/ services/notification-service/
COPY infra/avro/ infra/avro/

RUN chmod +x gradlew \
    && ./gradlew :services:notification-service:bootJar -x test --no-daemon

FROM eclipse-temurin:21-jre-jammy AS runtime

RUN groupadd --system app && useradd --system --gid app app
WORKDIR /app

COPY --from=build /workspace/services/notification-service/build/libs/*-SNAPSHOT.jar app.jar
RUN chown app:app app.jar

USER app
EXPOSE 8084

# Spring Boot 4 / Java 21 — 컨테이너 메모리 인식은 JDK 21 기본 동작
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
