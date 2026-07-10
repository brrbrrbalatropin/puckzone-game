package com.puckzone.game.websocket;

import com.puckzone.game.room.GameRoomService;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

/**
 * Traduce la caída del WebSocket de un jugador (cierre de pestaña, pérdida
 * de red) a la pausa de su partida. La identidad sale del Principal que
 * dejó el JWT del handshake, igual que en los mensajes STOMP. El estado
 * pausado se retransmite aquí mismo: con la sala fuera de PLAYING el game
 * loop deja de emitir, y el rival necesita enterarse de la pausa.
 */
@Component
public class GameDisconnectListener {

    private final GameRoomService rooms;
    private final SimpMessagingTemplate messaging;

    public GameDisconnectListener(GameRoomService rooms, SimpMessagingTemplate messaging) {
        this.rooms = rooms;
        this.messaging = messaging;
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        if (event.getUser() == null) {
            return;
        }
        rooms.playerDisconnected(event.getUser().getName(), event.getSessionId())
                .forEach(state ->
                        messaging.convertAndSend("/topic/game/" + state.getGameId(), state));
    }
}
