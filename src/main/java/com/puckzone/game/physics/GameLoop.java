package com.puckzone.game.physics;

import com.puckzone.game.bot.BotBrain;
import com.puckzone.game.config.GameProperties;
import com.puckzone.game.power.PowerManager;
import com.puckzone.game.room.GameEndService;
import com.puckzone.game.room.GameRoomService;
import com.puckzone.game.room.GameState;
import com.puckzone.game.room.OpponentType;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
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
    private final BotBrain bot;
    private final PowerManager powers;
    private final SimpMessagingTemplate messaging;
    private final GameProperties props;
    private final GameEndService gameEnd;
    private ScheduledExecutorService executor;
    /** Último barrido de salas viejas; corre ~1 vez/s, no en cada tick. */
    private long lastCleanupEpochMs;
    /**
     * Duración de cada tick global: ES la métrica de escalabilidad del
     * servicio (si el p99 se acerca al periodo del tick —16.6ms a 60Hz—
     * el loop está saturado y las partidas bajan de frecuencia).
     */
    private final Timer tickTimer;

    public GameLoop(GameRoomService rooms, PhysicsEngine engine, BotBrain bot,
                    PowerManager powers, SimpMessagingTemplate messaging,
                    GameProperties props, GameEndService gameEnd, MeterRegistry metrics) {
        this.rooms = rooms;
        this.engine = engine;
        this.bot = bot;
        this.powers = powers;
        this.messaging = messaging;
        this.props = props;
        this.gameEnd = gameEnd;
        this.tickTimer = Timer.builder("puckzone.game.tick")
                .description("Duración de un tick global del game loop")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(metrics);
        Gauge.builder("puckzone.game.rooms.playing", rooms, r -> r.activeGames().size())
                .description("Partidas avanzando en este tick")
                .register(metrics);
        Gauge.builder("puckzone.game.rooms.paused", rooms, r -> r.pausedGames().size())
                .description("Partidas pausadas esperando reconexión")
                .register(metrics);
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
        long tickStartedNanos = System.nanoTime();
        double dt = 1.0 / props.tickRate();
        long now = System.currentTimeMillis();
        for (GameState game : rooms.activeGames()) {
            try {
                if (game.getOpponentType() == OpponentType.BOT) {
                    bot.act(game, dt);
                }
                powers.tick(game, now);
                var outcome = engine.tick(game, dt);
                messaging.convertAndSend("/topic/game/" + game.getGameId(), game);
                if (outcome != TickOutcome.NONE) {
                    rooms.snapshot(game);
                }
                if (outcome == TickOutcome.FINISHED) {
                    log.info("Partida {} terminada {} - {}", game.getGameId(),
                            game.getScore1(), game.getScore2());
                    rooms.clearActiveIndex(game);
                    gameEnd.reportAsync(game);
                }
            } catch (RuntimeException e) {
                log.error("Tick falló en la partida {}: {}", game.getGameId(), e.getMessage());
            }
        }
        closeExpiredPauses();
        cleanupOncePerSecond();
        tickTimer.record(System.nanoTime() - tickStartedNanos, TimeUnit.NANOSECONDS);
    }

    /** Barre FINISHED retenidas y WAITING huérfanas (la limpieza no tiene afán). */
    private void cleanupOncePerSecond() {
        long now = System.currentTimeMillis();
        if (now - lastCleanupEpochMs < 1000) {
            return;
        }
        lastCleanupEpochMs = now;
        try {
            rooms.cleanupExpired(now);
        } catch (RuntimeException e) {
            log.error("El barrido de salas falló: {}", e.getMessage());
        }
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
