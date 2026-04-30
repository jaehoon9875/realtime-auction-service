pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

// JDK 툴체인이 로컬에 없을 때 Foojay API로 다운로드 (CI / 다른 머신에서 Java 21 누락 방지)
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "realtime-auction-service"

include("services:api-gateway")
include("services:user-service")
