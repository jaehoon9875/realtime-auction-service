package com.jaehoon.notification.support;

import org.testcontainers.DockerClientFactory;

/**
 * JUnit {@link org.junit.jupiter.api.condition.EnabledIf}용 Docker 가용성 판별.
 */
public final class DockerConditions {

    private DockerConditions() {}

    /** Docker 데몬에 연결 가능할 때만 true */
    public static boolean isDockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Exception e) {
            return false;
        }
    }
}
