# syntax=docker/dockerfile:1
# 빌드 컨텍스트: 저장소 루트 (infra/docker-compose.yml의 build.context: ..)
# Avro 생성(infra/avro) 포함 — auction-streams/build.gradle.kts의 generateAvro 태스크

FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace

COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle/ gradle/

# settings.gradle.kts에 등록된 모듈 — Gradle 구성 단계용 build 스크립트
COPY services/api-gateway/build.gradle.kts services/api-gateway/build.gradle.kts
COPY services/user-service/build.gradle.kts services/user-service/build.gradle.kts
COPY services/auction-service/build.gradle.kts services/auction-service/build.gradle.kts
COPY services/bid-service/build.gradle.kts services/bid-service/build.gradle.kts
COPY services/notification-service/build.gradle.kts services/notification-service/build.gradle.kts

COPY streams/auction-streams/ streams/auction-streams/
COPY infra/avro/ infra/avro/

# bootJar 결과물이 정확히 1개인지 검증. 현재 JAR 통째 복사 방식이며, 추후 jarmode=tools 레이어 추출 방식으로 전환 예정.
RUN chmod +x gradlew \
    && ./gradlew :streams:auction-streams:bootJar -x test --no-daemon \
    && jar=$(find /workspace/streams/auction-streams/build/libs -maxdepth 1 -type f -name '*.jar' ! -name '*-plain.jar') \
    && [ "$(echo "$jar" | grep -c .)" -eq 1 ] \
    && cp "$jar" /workspace/app.jar

FROM eclipse-temurin:21-jre-jammy AS runtime

RUN groupadd --system app && useradd --system --gid app app
WORKDIR /app

COPY --from=build /workspace/app.jar app.jar
RUN chown app:app app.jar

USER app
EXPOSE 8085

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
