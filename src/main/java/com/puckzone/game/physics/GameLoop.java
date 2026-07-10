package com.puckzone.game.physics;

import com.puckzone.game.bot.BotPaddle;
import com.puckzone.game.config.GameProperties;
import com.puckzone.game.room.GameEndService;
import com.puckzone.game.room.GameRoomService;
import com.puckzone.game.room.GameState;
import com.puckzone.game.room.OpponentType;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * El corazón del servicio: un único hilo que, {@code tick-rate} veces por
 * segundo, avanza la física de todas las partidas en PLAYING y publica el
 * estado resultante a {@code /topic/game/{id}} (los dos clientes de cada
 * sala están suscritos ahí). Redis solo se toca en eventos (gol, fin),
 * nunca en el camino caliente del tick.
 */
@Component
public class GameLoop {

    private static final Logger log = LoggerFactory.getLogger(GameLoop.class);

    private final GameRoomService rooms;
    private final PhysicsEngine engine;
    private final BotPaddle bot;
    private final SimpMessagingTemplate messaging;
    private final GameProperties props;
    private final GameEndService gameEnd;
    private ScheduledExecutorService executor;

    public GameLoop(GameRoomService rooms, PhysicsEngine engine, BotPaddle bot,
                    SimpMessagingTemplate messaging, GameProperties props,
                    GameEndService gameEnd) {
        this.rooms = rooms;
        this.engine = engine;
        this.bot = bot;
        this.messaging = messaging;
        this.props = props;
        this.gameEnd = gameEnd;
    }

    @PostConstruct
    void start() {
        long periodMillis = Math.round(1000.0 / props.tickRate());
        executor = Executors.newSingleThreadScheduledExecutor(
                Thread.ofPlatform().name("game-loop").factory());
        executor.scheduleAtFixedRate(this::tickAll, periodMillis, periodMillis, TimeUnit.MILLISECONDS);
        log.info("Game loop iniciado: {} ticks/s (cada {} ms)", props.tickRate(), periodMillis);
    }

    @PreDestroy
    void stop() {
        executor.shutdownNow();
    }

    /**
     * Un tick global. Cada partida se protege con try/catch: un error en
     * una sala no puede tumbar el loop de las demás.
     */
    private void tickAll() {
        double dt = 1.0 / props.tickRate();
        for (GameState game : rooms.activeGames()) {
            try {
                if (game.getOpponentType() == OpponentType.BOT) {
                    bot.act(game, dt);
                }
                var outcome = engine.tick(game, dt);
                messaging.convertAndSend("/topic/game/" + game.getGameId(), game);
                if (outcome != TickOutcome.NONE) {
                    rooms.snapshot(game);
                }
                if (outcome == TickOutcome.FINISHED) {
                    log.info("Partida {} terminada {} - {}", game.getGameId(),
                            game.getScore1(), game.getScore2());
                    gameEnd.reportAsync(game);
                }
            } catch (RuntimeException e) {
                log.error("Tick falló en la partida {}: {}", game.getGameId(), e.getMessage());
            }
        }
        closeExpiredPauses();
    }

    /**
     * Las salas PAUSED no tican, pero su ventana de gracia sí corre: si el
     * jugador caído no volvió a tiempo, la partida se cierra por abandono
     * a favor del que se quedó esperando.
     */
    private void closeExpiredPauses() {
        long now = System.currentTimeMillis();
        for (GameState game : rooms.pausedGames()) {
            if (game.getGraceDeadlineEpochMs() > 0 && now >= game.getGraceDeadlineEpochMs()) {
                try {
                    gameEnd.finishByGraceExpiry(game);
                } catch (RuntimeException e) {
                    log.error("Cierre por abandono falló en la partida {}: {}",
                            game.getGameId(), e.getMessage());
                }
            }
        }
    }
}
