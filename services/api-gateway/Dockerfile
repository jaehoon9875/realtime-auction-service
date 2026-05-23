# syntax=docker/dockerfile:1
# 빌드 컨텍스트: 저장소 루트 (infra/docker-compose.yml의 build.context: ..)

FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace

COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle/ gradle/

# settings.gradle.kts에 등록된 모듈 — Gradle 구성 단계용 build 스크립트
COPY services/user-service/build.gradle.kts services/user-service/build.gradle.kts
COPY services/auction-service/build.gradle.kts services/auction-service/build.gradle.kts
COPY services/bid-service/build.gradle.kts services/bid-service/build.gradle.kts
COPY services/notification-service/build.gradle.kts services/notification-service/build.gradle.kts
COPY streams/auction-streams/build.gradle.kts streams/auction-streams/build.gradle.kts

COPY services/api-gateway/ services/api-gateway/

RUN chmod +x gradlew \
    && ./gradlew :services:api-gateway:bootJar -x test --no-daemon \
    && cp "$(find /workspace/services/api-gateway/build/libs -maxdepth 1 -type f -name '*.jar' ! -name '*-plain.jar' | head -n1)" /workspace/app.jar

FROM eclipse-temurin:21-jre-jammy AS runtime

RUN groupadd --system app && useradd --system --gid app app
WORKDIR /app

COPY --from=build /workspace/app.jar app.jar
RUN chown app:app app.jar

USER app
EXPOSE 8080

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
