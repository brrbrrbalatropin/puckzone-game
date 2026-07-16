package com.puckzone.game.power;

import com.puckzone.game.config.GameProperties;
import com.puckzone.game.config.PowerProperties;
import com.puckzone.game.room.GameState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Ciclo de vida de los poderes de una partida; lo invoca el game loop en
 * cada tick, solo para salas en PLAYING. Stateless como el motor: todo
 * vive en el GameState. Hace tres cosas: vence efectos (restaurando lo que
 * el efecto tocó), hace aparecer pickups con su fase de parpadeo, y
 * resuelve la recogida por contacto de paleta aplicando el efecto de
 * inmediato. La física de los efectos ya activos (rebotes, zonas) es del
 * {@link com.puckzone.game.physics.PhysicsEngine}.
 */
@Component
public class PowerManager {

    private static final Logger log = LoggerFactory.getLogger(PowerManager.class);

    private final PowerProperties props;
    private final GameProperties gameProps;

    public PowerManager(PowerProperties props, GameProperties gameProps) {
        this.props = props;
        this.gameProps = gameProps;
    }

    /** Un paso del sistema de poderes. Recibe el reloj para ser testeable. */
    public void tick(GameState state, long now) {
        expireEffects(state, now);
        tickGhost(state, now);
        if (state.getPickup() == null) {
            maybeSpawn(state, now);
        } else {
            resolvePickup(state, now);
        }
    }

    /**
     * Gobierna la visibilidad durante el disco fantasma: oculto por
     * defecto, visible solo durante el destello brevísimo que deja cada
     * rebote (lo enciende el motor), y de vuelta a la normalidad cuando
     * la duración del fantasma se agota.
     */
    private void tickGhost(GameState state, long now) {
        if (state.getGhostUntilEpochMs() == 0) {
            return;
        }
        if (now >= state.getGhostUntilEpochMs()) {
            state.setGhostUntilEpochMs(0);
            state.setGhostFlashUntilEpochMs(0);
            state.setPuckVisible(true);
            return;
        }
        state.setPuckVisible(now < state.getGhostFlashUntilEpochMs());
    }

    /** Vence efectos; el escudo restaura el radio de la paleta del dueño. */
    private void expireEffects(GameState state, long now) {
        List<ActiveEffect> effects = state.getEffects();
        if (effects == null) {
            return;
        }
        effects.removeIf(effect -> {
            if (effect.expiresAtEpochMs() > now) {
                return false;
            }
            if (effect.type() == PowerType.SHIELD) {
                restorePaddleRadius(state, effect.owner());
            }
            return true;
        });
    }

    /**
     * Aparece un pickup nuevo cuando el intervalo se cumple. El reloj
     * arranca en el primer tick de la partida (no desde la creación de la
     * sala: en WAITING no debe correr).
     */
    private void maybeSpawn(GameState state, long now) {
        if (state.getLastPowerSpawnEpochMs() == 0) {
            state.setLastPowerSpawnEpochMs(now);
            return;
        }
        if (now - state.getLastPowerSpawnEpochMs() < props.spawnIntervalSeconds() * 1000L) {
            return;
        }
        var random = ThreadLocalRandom.current();
        PowerType type = PowerType.values()[random.nextInt(PowerType.values().length)];
        // Alterna mitades del tablero: el azar puro agrupaba varios pickups
        // seguidos en el mismo lado y se sentía injusto. El primero cae en
        // una mitad al azar; cada siguiente, en la contraria del anterior.
        int half = switch (state.getLastPickupHalf()) {
            case 1 -> 2;
            case 2 -> 1;
            default -> random.nextBoolean() ? 1 : 2;
        };
        state.setLastPickupHalf(half);
        // Lejos de bordes y porterías: entre el 15% y el 85% del tablero.
        double x = half == 1
                ? random.nextDouble(gameProps.boardWidth() * 0.15, gameProps.boardWidth() * 0.50)
                : random.nextDouble(gameProps.boardWidth() * 0.50, gameProps.boardWidth() * 0.85);
        double y = random.nextDouble(gameProps.boardHeight() * 0.15, gameProps.boardHeight() * 0.85);
        long activeFrom = now + props.blinkSeconds() * 1000L;
        state.setPickup(new PowerPickup(type, x, y, activeFrom,
                activeFrom + props.pickupLifetimeSeconds() * 1000L));
        log.info("Pickup {} apareció en la sala {}", type, state.getGameId());
    }

    /** Expira el pickup ignorado o lo entrega a la primera paleta que lo toque. */
    private void resolvePickup(GameState state, long now) {
        PowerPickup pickup = state.getPickup();
        if (now >= pickup.expiresAtEpochMs()) {
            state.setPickup(null);
            state.setLastPowerSpawnEpochMs(now);
            return;
        }
        if (now < pickup.activeFromEpochMs()) {
            return; // parpadeando: todavía no se puede recoger
        }
        int collector = touchingPlayer(state, pickup);
        if (collector == 0) {
            return;
        }
        state.setPickup(null);
        state.setLastPowerSpawnEpochMs(now);
        apply(state, pickup, collector, now);
        log.info("Jugador {} recogió {} en la sala {}", collector, pickup.type(), state.getGameId());
    }

    private int touchingPlayer(GameState state, PowerPickup pickup) {
        if (touches(pickup, state.getPaddle1X(), state.getPaddle1Y(), paddleRadius(state, 1))) {
            return 1;
        }
        if (touches(pickup, state.getPaddle2X(), state.getPaddle2Y(), paddleRadius(state, 2))) {
            return 2;
        }
        return 0;
    }

    private boolean touches(PowerPickup pickup, double paddleX, double paddleY, double paddleRadius) {
        return Math.hypot(pickup.x() - paddleX, pickup.y() - paddleY)
                <= paddleRadius + props.pickupRadius();
    }

    private void apply(GameState state, PowerPickup pickup, int collector, long now) {
        long expiresAt = now + props.effectDurationSeconds() * 1000L;
        switch (pickup.type()) {
            case OBSTACLE -> state.getEffects().add(new ActiveEffect(PowerType.OBSTACLE,
                    collector, pickup.x(), pickup.y(), props.obstacleRadius(), expiresAt));
            case FAST_ZONE -> state.getEffects().add(new ActiveEffect(PowerType.FAST_ZONE,
                    collector, pickup.x(), pickup.y(), props.zoneRadius(), expiresAt));
            case SLOW_ZONE -> state.getEffects().add(new ActiveEffect(PowerType.SLOW_ZONE,
                    collector, pickup.x(), pickup.y(), props.zoneRadius(), expiresAt));
            case SHIELD -> applyShield(state, collector, expiresAt);
            case GHOST_PUCK -> applyGhost(state, collector, now);
            case CHAOS -> state.setChaosArmed(true);
        }
    }

    /**
     * Disco fantasma por una duración aleatoria dentro del rango
     * configurado. La entrada en effects es solo para el HUD (el badge y
     * su vencimiento); la visibilidad la gobierna {@link #tickGhost}.
     */
    private void applyGhost(GameState state, int owner, long now) {
        long duration = ThreadLocalRandom.current().nextLong(
                props.ghostMinSeconds() * 1000L, props.ghostMaxSeconds() * 1000L + 1);
        state.setGhostUntilEpochMs(now + duration);
        state.setGhostFlashUntilEpochMs(0);
        state.setPuckVisible(false);
        state.getEffects().removeIf(effect -> effect.type() == PowerType.GHOST_PUCK);
        state.getEffects().add(new ActiveEffect(PowerType.GHOST_PUCK, owner, 0, 0, 0, now + duration));
    }

    /**
     * Dobla el radio de la paleta del dueño. Si ya tenía escudo, el nuevo
     * reemplaza al anterior (extiende la duración, no la apila).
     */
    private void applyShield(GameState state, int owner, long expiresAt) {
        state.getEffects().removeIf(effect ->
                effect.type() == PowerType.SHIELD && effect.owner() == owner);
        double doubled = gameProps.paddleRadius() * 2.0;
        if (owner == 1) {
            state.setPaddle1Radius(doubled);
        } else {
            state.setPaddle2Radius(doubled);
        }
        state.getEffects().add(new ActiveEffect(PowerType.SHIELD, owner, 0, 0, 0, expiresAt));
    }

    private void restorePaddleRadius(GameState state, int owner) {
        if (owner == 1) {
            state.setPaddle1Radius(gameProps.paddleRadius());
        } else {
            state.setPaddle2Radius(gameProps.paddleRadius());
        }
    }

    private double paddleRadius(GameState state, int player) {
        double radius = player == 1 ? state.getPaddle1Radius() : state.getPaddle2Radius();
        return radius > 0 ? radius : gameProps.paddleRadius();
    }
}
