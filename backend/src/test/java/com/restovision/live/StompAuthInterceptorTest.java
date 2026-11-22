package com.restovision.live;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.restovision.security.AuthService;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

@SpringBootTest
class StompAuthInterceptorTest {
    @Autowired StompAuthInterceptor interceptor;
    @Autowired AuthService auth;

    private static Message<byte[]> frame(StompCommand command, Map<String, Object> session, String header, String value,
                                         String destination) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setSessionAttributes(session);
        if (header != null) {
            accessor.setNativeHeader(header, value);
        }
        if (destination != null) {
            accessor.setDestination(destination);
        }
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Map<String, Object> connectedSession() {
        Map<String, Object> session = new HashMap<>();
        String token = auth.login("demo@restovision.local", "demo1234").token();
        interceptor.preSend(frame(StompCommand.CONNECT, session, "Authorization", "Bearer " + token, null), null);
        return session;
    }

    @Test
    void connectingNeedsAValidToken() {
        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.CONNECT, new HashMap<>(), null, null, null), null))
                .isInstanceOf(MessageDeliveryException.class);
        assertThatThrownBy(() -> interceptor.preSend(
                frame(StompCommand.CONNECT, new HashMap<>(), "Authorization", "Bearer faux.jeton.ici", null), null))
                .isInstanceOf(MessageDeliveryException.class);
        assertThat(connectedSession()).containsEntry("restaurant", "demo");
    }

    @Test
    void aScreenOnlyFollowsItsOwnRestaurant() {
        Map<String, Object> session = connectedSession();
        assertThat(interceptor.preSend(
                frame(StompCommand.SUBSCRIBE, session, null, null, "/topic/restaurants/demo/zones"), null)).isNotNull();
        assertThatThrownBy(() -> interceptor.preSend(
                frame(StompCommand.SUBSCRIBE, session, null, null, "/topic/restaurants/autre/zones"), null))
                .isInstanceOf(MessageDeliveryException.class);
        assertThatThrownBy(() -> interceptor.preSend(
                frame(StompCommand.SUBSCRIBE, session, null, null, "/topic/restaurants/demo-bis/zones"), null))
                .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    void subscribingWithoutConnectingFirstIsRefused() {
        assertThatThrownBy(() -> interceptor.preSend(
                frame(StompCommand.SUBSCRIBE, new HashMap<>(), null, null, "/topic/restaurants/demo/zones"), null))
                .isInstanceOf(MessageDeliveryException.class);
    }
}
