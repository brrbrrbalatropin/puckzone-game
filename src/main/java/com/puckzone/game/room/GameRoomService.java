package com.puckzone.game.room;

import com.puckzone.game.bot.BotProfile;
import com.puckzone.game.config.GameProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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
    /**
     * Sesión WS vigente de cada jugador (userId → sessionId del último join).
     * Permite ignorar el disconnect de una sesión vieja: si el jugador abrió
     * otra pestaña y se unió desde ella, cerrar la primera no pausa nada.
     */
    private final Map<String, String> sessionsByUser = new ConcurrentHashMap<>();
    private final GameProperties properties;
    private final GameStateRepository repository;
    private final ApplicationEventPublisher eventPublisher;

    public GameRoomService(GameProperties properties, GameStateRepository repository,
                           ApplicationEventPublisher eventPublisher) {
        this.properties = properties;
        this.repository = repository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Crea la sala que anuncia matchmaking, con las piezas en posición
     * inicial: paletas a cada lado, disco quieto en el centro (el saque lo
     * da el motor cuando la partida pasa a PLAYING). Idempotente: si la
     * sala ya existe (reintento de matchmaking) se devuelve la existente.
     */
    public GameState create(String matchId, Player player1, Player player2,
                            OpponentType opponentType, boolean friendly, Integer player1Rating) {
        return rooms.computeIfAbsent(matchId, id -> {
            var state = GameState.builder()
                    .gameId(id)
                    .player1(player1)
                    .player2(player2)
                    .botLevel(opponentType == OpponentType.BOT
                            ? BotProfile.levelForElo(player1Rating) : 0)
                    .opponentType(opponentType)
                    .friendly(friendly)
                    .status(GameStatus.WAITING)
                    .puckX(properties.boardWidth() / 2.0)
                    .puckY(properties.boardHeight() / 2.0)
                    .paddle1X(properties.paddleRadius() * 2.0)
                    .paddle1Y(properties.boardHeight() / 2.0)
                    .paddle2X(properties.boardWidth() - properties.paddleRadius() * 2.0)
                    .paddle2Y(properties.boardHeight() / 2.0)
                    .paddle1Radius(properties.paddleRadius())
                    .paddle2Radius(properties.paddleRadius())
                    .puckVisible(true)
                    .effects(new ArrayList<>())
                    .createdAtEpochMs(System.currentTimeMillis())
                    .build();
            snapshot(state);
            log.info("Sala {}{} creada: {} vs {}", id, friendly ? " (amistosa)" : "",
                    player1.username(),
                    opponentType == OpponentType.BOT ? "BOT" : player2.username());
            return state;
        });
    }

    public Optional<GameState> find(String gameId) {
        return Optional.ofNullable(rooms.get(gameId));
    }

    /** Partidas que el game loop debe avanzar en el próximo tick. */
    public List<GameState> activeGames() {
        return rooms.values().stream()
                .filter(state -> state.getStatus() == GameStatus.PLAYING)
                .toList();
    }

    /** Partidas pausadas, para que el loop vigile sus ventanas de gracia. */
    public List<GameState> pausedGames() {
        return rooms.values().stream()
                .filter(state -> state.getStatus() == GameStatus.PAUSED)
                .toList();
    }

    /**
     * Partida viva del usuario, para que el lobby le ofrezca volver. Si
     * hubiera más de una (no debería: matchmaking empareja de a una), gana
     * la que ya arrancó sobre una WAITING huérfana.
     */
    public Optional<GameState> activeGameOf(String userId) {
        return rooms.values().stream()
                .filter(state -> state.getStatus() != GameStatus.FINISHED)
                .filter(state -> isPlayer(state, userId))
                .max(Comparator.comparingLong(GameState::getStartedAtEpochMs));
    }

    /**
     * Marca al jugador como conectado al WebSocket y recuerda su sesión
     * vigente. Cuando todos los humanos requeridos están dentro (solo
     * player1 si es vs bot), la partida pasa a PLAYING y el motor la toma
     * en el siguiente tick; si estaba PAUSED por una desconexión, se
     * reanuda donde quedó (el reloj de la partida no se reinicia).
     */
    public Optional<GameState> playerConnected(String gameId, String userId, String sessionId) {
        return find(gameId).map(state -> {
            if (state.getPlayer1().userId().equals(userId)) {
                state.setPlayer1Connected(true);
            } else if (state.getPlayer2() != null && state.getPlayer2().userId().equals(userId)) {
                state.setPlayer2Connected(true);
            } else {
                log.warn("Usuario {} no pertenece a la sala {}", userId, gameId);
                return state;
            }
            sessionsByUser.put(userId, sessionId);
            if (state.getStatus() == GameStatus.WAITING && state.allPlayersConnected()) {
                state.setStatus(GameStatus.PLAYING);
                long now = System.currentTimeMillis();
                state.setStartedAtEpochMs(now);
                // El mismo respiro de anuncio que tras un gol, para arrancar.
                state.setServeAtEpochMs(now + properties.goalPauseSeconds() * 1000L);
                log.info("Sala {} completa: la partida arranca", gameId);
            } else if (state.getStatus() == GameStatus.PAUSED && state.allPlayersConnected()) {
                state.setStatus(GameStatus.PLAYING);
                state.setGraceDeadlineEpochMs(0);
                log.info("Sala {} reanudada: {} se reconectó a tiempo", gameId, userId);
            }
            snapshot(state);
            return state;
        });
    }

    /**
     * Reacción a la caída del WebSocket de un jugador. Solo actúa si la
     * sesión caída es la vigente (un disconnect de una pestaña vieja no
     * cuenta). Un desconectado no está en NINGUNA sala: se le marca en
     * todas sus salas vivas y las que estaban corriendo se pausan con su
     * ventana de gracia; el que la ventana expire sin reconexión lo decide
     * el game loop. Devuelve las salas afectadas para que quien escucha
     * retransmita el estado (con la sala PAUSED el loop deja de emitir).
     */
    public List<GameState> playerDisconnected(String userId, String sessionId) {
        if (!sessionsByUser.remove(userId, sessionId)) {
            return List.of();
        }
        return rooms.values().stream()
                .filter(state -> state.getStatus() != GameStatus.FINISHED)
                .filter(state -> isPlayer(state, userId))
                .map(state -> {
                    if (state.getPlayer1().userId().equals(userId)) {
                        state.setPlayer1Connected(false);
                    } else {
                        state.setPlayer2Connected(false);
                    }
                    if (state.getStatus() == GameStatus.PLAYING) {
                        state.setStatus(GameStatus.PAUSED);
                        state.setGraceDeadlineEpochMs(System.currentTimeMillis()
                                + properties.disconnectGraceSeconds() * 1000L);
                        log.info("Sala {} pausada: {} se desconectó ({}s de gracia)",
                                state.getGameId(), userId, properties.disconnectGraceSeconds());
                    }
                    snapshot(state);
                    return state;
                })
                .toList();
    }

    /** ¿El usuario juega en esta sala? */
    private boolean isPlayer(GameState state, String userId) {
        return state.getPlayer1().userId().equals(userId)
                || (state.getPlayer2() != null && state.getPlayer2().userId().equals(userId));
    }

    /**
     * Barrido del mapa en memoria (lo invoca el game loop ~1 vez/s): salen
     * las FINISHED que agotaron su retención — se retienen un rato para
     * que un jugador que llegue tarde aún reciba el estado final — y las
     * WAITING huérfanas donde alguien nunca entró. Redis no se toca: su
     * snapshot es el respaldo consultable y expira por TTL. Por cada sala
     * eliminada se publica {@link GameRoomRemovedEvent} para que otros
     * limpien su estado asociado.
     */
    public void cleanupExpired(long nowEpochMs) {
        long finishedCutoff = nowEpochMs - properties.finishedRetentionSeconds() * 1000L;
        long waitingCutoff = nowEpochMs - properties.waitingTimeoutSeconds() * 1000L;
        rooms.values().removeIf(state -> {
            boolean expired = switch (state.getStatus()) {
                case FINISHED -> state.getFinishedAtEpochMs() > 0
                        && state.getFinishedAtEpochMs() <= finishedCutoff;
                case WAITING -> state.getCreatedAtEpochMs() <= waitingCutoff;
                default -> false;
            };
            if (expired) {
                log.info("Sala {} eliminada del mapa ({})", state.getGameId(), state.getStatus());
                eventPublisher.publishEvent(new GameRoomRemovedEvent(state.getGameId()));
            }
            return expired;
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
