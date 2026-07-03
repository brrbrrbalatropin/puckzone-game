package com.puckzone.game.websocket;

import com.puckzone.game.physics.PhysicsEngine;
import com.puckzone.game.room.GameRoomService;
import com.puckzone.game.room.GameState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

/**
 * Entrada STOMP de los jugadores. Los clientes solo envían inputs por
 * {@code /app/**}; el estado siempre viaja de vuelta por
 * {@code /topic/game/{id}}, calculado por el servidor.
 */
@Controller
public class GameSocketController {

    private static final Logger log = LoggerFactory.getLogger(GameSocketController.class);

    private final GameRoomService rooms;
    private final PhysicsEngine engine;
    private final SimpMessagingTemplate messaging;

    public GameSocketController(GameRoomService rooms, PhysicsEngine engine,
                                SimpMessagingTemplate messaging) {
        this.rooms = rooms;
        this.engine = engine;
        this.messaging = messaging;
    }

    /**
     * El jugador anuncia que está listo. Cuando todos los requeridos se
     * han unido, la sala pasa a PLAYING y el loop la toma. Se publica el
     * estado de inmediato para que el recién llegado pinte el tablero sin
     * esperar a que la partida arranque.
     */
    @MessageMapping("/game/{gameId}/join")
    public void join(@DestinationVariable String gameId, JoinMessage message) {
        rooms.playerConnected(gameId, message.userId()).ifPresentOrElse(
                state -> messaging.convertAndSend("/topic/game/" + gameId, state),
                () -> log.warn("Join a la sala inexistente {}", gameId));
    }

    /**
     * Input de mouse. Solo se aplica si el userId pertenece a la sala, y
     * siempre recortado a la mitad de cancha del jugador. No responde
     * nada: el game loop transmite el estado resultante a 60Hz.
     */
    @MessageMapping("/game/{gameId}/paddle")
    public void movePaddle(@DestinationVariable String gameId, PaddleMoveMessage message) {
        rooms.find(gameId).ifPresent(state -> {
            int player = playerNumber(state, message.userId());
            if (player == 0) {
                log.warn("Usuario {} intentó mover paleta en la sala ajena {}", message.userId(), gameId);
                return;
            }
            engine.movePaddle(state, player, message.x(), message.y());
        });
    }

    private int playerNumber(GameState state, Long userId) {
        if (state.getPlayer1().userId().equals(userId)) {
            return 1;
        }
        if (state.getPlayer2() != null && state.getPlayer2().userId().equals(userId)) {
            return 2;
        }
        return 0;
    }
}
