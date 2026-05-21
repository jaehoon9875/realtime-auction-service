package com.jaehoon.notification.kafka;

/**
 * NotificationEvent → WebSocket 메시지 변환 실패를 나타내는 예외.
 *
 * <p>다음 두 가지 경우에 발생한다:
 * <ul>
 *   <li>지원하지 않는 notificationType이 수신된 경우</li>
 *   <li>JSON 직렬화 과정에서 예상치 못한 오류가 발생한 경우</li>
 * </ul>
 *
 * <p>{@link NotificationEventConsumer}에서 이 예외를 catch해 경고 로그를 남기고
 * 해당 이벤트를 스킵한다. Kafka 리스너 컨테이너까지 전파되지 않으므로
 * 파티션 폴링이 중단되지 않는다.
 */
public class NotificationMappingException extends RuntimeException {

    public NotificationMappingException(String message) {
        super(message);
    }

    public NotificationMappingException(String message, Throwable cause) {
        super(message, cause);
    }
}
