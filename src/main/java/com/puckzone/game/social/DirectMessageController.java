package com.puckzone.game.social;

import com.puckzone.game.social.DirectMessageService.MessageView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;

/**
 * Entrada STOMP de los mensajes directos: el cliente publica en
 * {@code /app/dm} y el mensaje persistido sale por la cola personal
 * {@code /user/queue/dm} de AMBOS participantes — al remitente le sirve de
 * eco (confirma que se guardó) y mantiene sincronizadas sus otras
 * pestañas. Los mensajes inválidos se descartan en silencio, como en el
 * chat del lobby: las reglas duras viven en el servicio.
 */
@Controller
public class DirectMessageController {

    private static final Logger log = LoggerFactory.getLogger(DirectMessageController.class);

    /** Payload del cliente: a quién y qué. La identidad sale del Principal. */
    public record DirectMessageSend(String toUserId, String text) {
    }

    private final DirectMessageService directMessages;
    private final SimpMessagingTemplate messaging;

    public DirectMessageController(DirectMessageService directMessages,
                                   SimpMessagingTemplate messaging) {
        this.directMessages = directMessages;
        this.messaging = messaging;
    }

    @MessageMapping("/dm")
    public void send(DirectMessageSend message, Principal principal) {
        try {
            MessageView view = directMessages.send(
                    principal.getName(), message.toUserId(), message.text());
            messaging.convertAndSendToUser(view.recipientId(), "/queue/dm", view);
            messaging.convertAndSendToUser(view.senderId(), "/queue/dm", view);
        } catch (SocialException e) {
            log.debug("DM descartado de {}: {}", principal.getName(), e.getMessage());
        }
    }
}
