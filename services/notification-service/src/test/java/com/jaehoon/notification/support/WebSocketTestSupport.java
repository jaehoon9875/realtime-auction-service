package com.jaehoon.notification.support;

import org.mockito.Mockito;
import org.reactivestreams.Publisher;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * WebSocketSession 테스트용 목 세션 생성.
 */
public final class WebSocketTestSupport {

    private WebSocketTestSupport() {}

    /**
     * send() 호출 시 수신한 텍스트 페이로드를 {@code receivedMessages}에 적재한다.
     */
    public static WebSocketSession mockSession(String sessionId, List<String> receivedMessages) {
        WebSocketSession session = Mockito.mock(WebSocketSession.class);
        when(session.getId()).thenReturn(sessionId);
        when(session.textMessage(anyString()))
                .thenAnswer(
                        invocation -> {
                            String payload = invocation.getArgument(0);
                            WebSocketMessage message = Mockito.mock(WebSocketMessage.class);
                            when(message.getPayloadAsText()).thenReturn(payload);
                            return message;
                        });
        when(session.send(any(Publisher.class)))
                .thenAnswer(
                        invocation -> {
                            @SuppressWarnings("unchecked")
                            Publisher<? extends WebSocketMessage> publisher =
                                    invocation.getArgument(0);
                            return Flux.from(publisher)
                                    .doOnNext(msg -> receivedMessages.add(msg.getPayloadAsText()))
                                    .then();
                        });
        return session;
    }
}
