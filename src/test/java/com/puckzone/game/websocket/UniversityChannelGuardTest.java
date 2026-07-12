package com.puckzone.game.websocket;

import com.puckzone.game.security.StompPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

/**
 * Nadie escucha el canal de una universidad ajena: el SUBSCRIBE se
 * descarta salvo que la universidad del JWT coincida; el resto de frames
 * y topics pasan intactos.
 */
class UniversityChannelGuardTest {

    private final UniversityChannelGuard guard = new UniversityChannelGuard();
    private final MessageChannel channel = mock(MessageChannel.class);

    private Message<byte[]> subscribeTo(String destination, StompPrincipal user) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination(destination);
        accessor.setUser(user);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    @Test
    void dejaPasarLaSuscripcionALaPropiaUniversidad() {
        var msg = subscribeTo("/topic/lobby/chat/university/escuelaing",
                new StompPrincipal("u1", "daniel", "escuelaing"));
        assertNotNull(guard.preSend(msg, channel));
    }

    @Test
    void bloqueaLaSuscripcionAUnaUniversidadAjena() {
        var msg = subscribeTo("/topic/lobby/chat/university/unal",
                new StompPrincipal("u1", "daniel", "escuelaing"));
        assertNull(guard.preSend(msg, channel));
    }

    @Test
    void bloqueaSinUniversidadEnElToken() {
        var msg = subscribeTo("/topic/lobby/chat/university/escuelaing",
                new StompPrincipal("u1", "daniel", null));
        assertNull(guard.preSend(msg, channel));
    }

    @Test
    void losDemasTopicsYFramesNoSeTocan() {
        var otherTopic = subscribeTo("/topic/lobby/chat",
                new StompPrincipal("u1", "daniel", "escuelaing"));
        assertNotNull(guard.preSend(otherTopic, channel));

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setDestination("/app/lobby/chat/university");
        var sendFrame = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        assertNotNull(guard.preSend(sendFrame, channel));
    }
}
