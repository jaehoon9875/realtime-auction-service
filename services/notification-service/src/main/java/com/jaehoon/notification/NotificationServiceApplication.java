package com.jaehoon.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 실시간 알림 서비스 애플리케이션 진입점.
 */
@SpringBootApplication
public class NotificationServiceApplication {

    /**
     * 실시간 알림 서비스 애플리케이션을 실행한다.
     * 
     * @param args 애플리케이션 시작 인자
     */
    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}
