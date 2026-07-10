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

import java.security.Principal;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Entrada STOMP de los jugadores. Los clientes solo envían inputs por
 * {@code /app/**}; el estado siempre viaja de vuelta por
 * {@code /topic/game/{id}}, calculado por el servidor. La identidad de
 * cada mensaje es el {@link Principal} que dejó el JWT del handshake:
 * un cliente no puede actuar en nombre de otro jugador.
 */
@Controller
public class GameSocketController {

    private static final Logger log = LoggerFactory.getLogger(GameSocketController.class);

    /** Ids de emote permitidos; el frontend los traduce a su emoji. */
    private static final Set<String> ALLOWED_EMOTES =
            Set.of("THUMBS_UP", "LAUGH", "WOW", "CRY", "ANGRY", "GG");
    /** Anti-spam: mínimo entre emotes del mismo jugador. */
    private static final long EMOTE_COOLDOWN_MS = 1000;

    private final GameRoomService rooms;
    private final PhysicsEngine engine;
    private final SimpMessagingTemplate messaging;
    /** Último emote por sala+jugador. Vive lo que viva la instancia, como las salas. */
    private final Map<String, Long> lastEmoteAt = new ConcurrentHashMap<>();

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
    public void join(@DestinationVariable String gameId, Principal principal) {
        rooms.playerConnected(gameId, principal.getName()).ifPresentOrElse(
                state -> messaging.convertAndSend("/topic/game/" + gameId, state),
                () -> log.warn("Join a la sala inexistente {}", gameId));
    }

    /**
     * Input de mouse. Solo se aplica si el userId pertenece a la sala, y
     * siempre recortado a la mitad de cancha del jugador. No responde
     * nada: el game loop transmite el estado resultante a 60Hz.
     */
    @MessageMapping("/game/{gameId}/paddle")
    public void movePaddle(@DestinationVariable String gameId, PaddleMoveMessage message,
                           Principal principal) {
        rooms.find(gameId).ifPresent(state -> {
            int player = playerNumber(state, principal.getName());
            if (player == 0) {
                log.warn("Usuario {} intentó mover paleta en la sala ajena {}", principal.getName(), gameId);
                return;
            }
            engine.movePaddle(state, player, message.x(), message.y());
        });
    }

    /**
     * Emote hacia el rival. Solo ids de la lista blanca, solo jugadores de la
     * sala y con cooldown por jugador (anti-spam). Se retransmite por un topic
     * aparte para no ensuciar el stream de estados a 60Hz.
     */
    @MessageMapping("/game/{gameId}/emote")
    public void emote(@DestinationVariable String gameId, EmoteMessage message,
                      Principal principal) {
        if (!ALLOWED_EMOTES.contains(message.emote())) {
            return;
        }
        String userId = principal.getName();
        rooms.find(gameId).ifPresent(state -> {
            if (playerNumber(state, userId) == 0) {
                log.warn("Usuario {} intentó mandar emote en la sala ajena {}", userId, gameId);
                return;
            }
            // compute es atómico por clave: sin esto, dos emotes seguidos se
            // procesan en hilos distintos del broker y ambos pasan el chequeo.
            String key = gameId + ":" + userId;
            long now = System.currentTimeMillis();
            boolean[] allowed = {false};
            lastEmoteAt.compute(key, (k, last) -> {
                if (last != null && now - last < EMOTE_COOLDOWN_MS) {
                    return last;
                }
                allowed[0] = true;
                return now;
            });
            if (!allowed[0]) {
                return;
            }
            messaging.convertAndSend("/topic/game/" + gameId + "/emotes",
                    new EmoteBroadcast(userId, message.emote()));
        });
    }

    private int playerNumber(GameState state, String userId) {
        if (state.getPlayer1().userId().equals(userId)) {
            return 1;
        }
        if (state.getPlayer2() != null && state.getPlayer2().userId().equals(userId)) {
            return 2;
        }
        return 0;
    }
}
