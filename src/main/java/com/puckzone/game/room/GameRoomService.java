package com.puckzone.game.room;

import com.puckzone.game.config.GameProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registro de las salas vivas de esta instancia. La verdad del juego está
 * en el mapa en memoria (el motor tica sobre estos objetos); Redis guarda
 * fotos en eventos clave como respaldo consultable.
 *
 * <p>Mapa concurrente porque lo tocan hilos distintos: el request de
 * matchmaking que crea la sala, los mensajes STOMP de cada jugador y el
 * hilo del game loop.
 */
@Service
public class GameRoomService {

    private static final Logger log = LoggerFactory.getLogger(GameRoomService.class);

    private final Map<String, GameState> rooms = new ConcurrentHashMap<>();
    private final GameProperties properties;
    private final GameStateRepository repository;

    public GameRoomService(GameProperties properties, GameStateRepository repository) {
        this.properties = properties;
        this.repository = repository;
    }

    /**
     * Crea la sala que anuncia matchmaking, con las piezas en posición
     * inicial: paletas a cada lado, disco quieto en el centro (el saque lo
     * da el motor cuando la partida pasa a PLAYING). Idempotente: si la
     * sala ya existe (reintento de matchmaking) se devuelve la existente.
     */
    public GameState create(String matchId, Player player1, Player player2, OpponentType opponentType) {
        return rooms.computeIfAbsent(matchId, id -> {
            var state = GameState.builder()
                    .gameId(id)
                    .player1(player1)
                    .player2(player2)
                    .opponentType(opponentType)
                    .status(GameStatus.WAITING)
                    .puckX(properties.boardWidth() / 2.0)
                    .puckY(properties.boardHeight() / 2.0)
                    .paddle1X(properties.paddleRadius() * 2.0)
                    .paddle1Y(properties.boardHeight() / 2.0)
                    .paddle2X(properties.boardWidth() - properties.paddleRadius() * 2.0)
                    .paddle2Y(properties.boardHeight() / 2.0)
                    .build();
            snapshot(state);
            log.info("Sala {} creada: {} vs {}", id, player1.username(),
                    opponentType == OpponentType.BOT ? "BOT" : player2.username());
            return state;
        });
    }

    public Optional<GameState> find(String gameId) {
        return Optional.ofNullable(rooms.get(gameId));
    }

    /**
     * Marca al jugador como conectado al WebSocket. Cuando todos los
     * humanos requeridos están dentro (solo player1 si es vs bot), la
     * partida pasa a PLAYING y el motor la toma en el siguiente tick.
     */
    public Optional<GameState> playerConnected(String gameId, Long userId) {
        return find(gameId).map(state -> {
            if (state.getPlayer1().userId().equals(userId)) {
                state.setPlayer1Connected(true);
            } else if (state.getPlayer2() != null && state.getPlayer2().userId().equals(userId)) {
                state.setPlayer2Connected(true);
            } else {
                log.warn("Usuario {} no pertenece a la sala {}", userId, gameId);
                return state;
            }
            if (state.getStatus() == GameStatus.WAITING && state.allPlayersConnected()) {
                state.setStatus(GameStatus.PLAYING);
                log.info("Sala {} completa: la partida arranca", gameId);
            }
            snapshot(state);
            return state;
        });
    }

    /** Foto a Redis, best-effort: si Redis no está, el juego sigue. */
    public void snapshot(GameState state) {
        try {
            repository.save(state);
        } catch (RuntimeException e) {
            log.warn("No se pudo guardar el snapshot de {} en Redis: {}", state.getGameId(), e.getMessage());
        }
    }
}
