package com.puckzone.game.websocket;

import com.puckzone.game.security.StompPrincipal;
import org.jspecify.annotations.Nullable;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;

/**
 * El broker simple no restringe suscripciones, así que sin esto cualquiera
 * podría escuchar el canal de otra universidad. Deja pasar cualquier frame excepto el
 * SUBSCRIBE a {@code /topic/lobby/chat/university/{uni}} de quien no
 * pertenece a esa universidad según su JWT (el frame se descarta en
 * silencio, igual que los mensajes inválidos del chat).
 */
public class UniversityChannelGuard implements ChannelInterceptor {

    static final String UNIVERSITY_TOPIC_PREFIX = "/topic/lobby/chat/university/";

    @Override
    public @Nullable Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        if (accessor.getCommand() != StompCommand.SUBSCRIBE) {
            return message;
        }
        String destination = accessor.getDestination();
        if (destination == null || !destination.startsWith(UNIVERSITY_TOPIC_PREFIX)) {
            return message;
        }
        String requested = destination.substring(UNIVERSITY_TOPIC_PREFIX.length());
        return accessor.getUser() instanceof StompPrincipal p
                && requested.equals(p.university())
                ? message
                : null;
    }
}
