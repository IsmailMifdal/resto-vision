package com.restovision.live;

import java.util.Map;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;

/**
 * Contrôle les écrans en direct : il faut un jeton valide pour se connecter,
 * et on ne peut s'abonner qu'aux sujets de son propre restaurant.
 */
@Component
public class StompAuthInterceptor implements ChannelInterceptor {
    static final String RESTAURANT = "restaurant";
    private final JwtDecoder jwtDecoder;

    public StompAuthInterceptor(JwtDecoder jwtDecoder) {
        this.jwtDecoder = jwtDecoder;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }
        Map<String, Object> session = accessor.getSessionAttributes();
        switch (accessor.getCommand()) {
            case CONNECT -> {
                String header = accessor.getFirstNativeHeader("Authorization");
                if (header == null || !header.startsWith("Bearer ") || session == null) {
                    throw new MessageDeliveryException("Jeton manquant");
                }
                try {
                    Jwt jwt = jwtDecoder.decode(header.substring(7));
                    String restaurant = jwt.getClaimAsString(RESTAURANT);
                    if (restaurant == null) {
                        throw new MessageDeliveryException("Jeton sans restaurant");
                    }
                    session.put(RESTAURANT, restaurant);
                } catch (JwtException e) {
                    throw new MessageDeliveryException("Jeton invalide");
                }
            }
            case SUBSCRIBE -> {
                Object restaurant = session == null ? null : session.get(RESTAURANT);
                String destination = accessor.getDestination();
                if (restaurant == null || destination == null
                        || !destination.startsWith("/topic/restaurants/" + restaurant + "/")) {
                    throw new MessageDeliveryException("Abonnement refusé");
                }
            }
            case SEND -> throw new MessageDeliveryException("Les écrans ne publient rien");
            default -> { }
        }
        return message;
    }
}
