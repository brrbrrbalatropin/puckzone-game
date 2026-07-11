package com.puckzone.game.physics;

import com.puckzone.game.config.GameProperties;
import com.puckzone.game.power.ActiveEffect;
import com.puckzone.game.power.PowerType;
import com.puckzone.game.room.FinishReason;
import com.puckzone.game.room.GameState;
import com.puckzone.game.room.GameStatus;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Motor de física 2D del air hockey. Stateless: todo el estado vive en el
 * {@link GameState} que recibe; por eso un solo engine sirve a todas las
 * partidas a la vez.
 *
 * <p>Modelo de colisión paleta-disco: el disco sale en la dirección de la
 * normal del contacto (centro de paleta → centro de disco) con un impulso
 * extra acotado por {@code max-puck-speed}. Así el jugador dirige el disco
 * según dónde lo golpea, sin necesidad de rastrear la velocidad del mouse.
 */
@Component
public class PhysicsEngine {

    /** Impulso extra que gana el disco en cada golpe de paleta (px/s). */
    private static final double HIT_BOOST = 60;
    /** Velocidad mínima con la que sale el disco tras un golpe (px/s). */
    private static final double MIN_HIT_SPEED = 350;
    /** Semiapertura del ángulo aleatorio de saque (grados). */
    private static final double SERVE_ANGLE = 30;
    /**
     * Zona rápida: aceleración proporcional por segundo dentro de la zona.
     * Agresiva a propósito: el disco cruza la zona en ~0.3s y con tasas
     * tímidas el efecto no se sentía en el juego real.
     */
    private static final double FAST_ZONE_ACCEL = 4.0;
    /** Zona lenta: frenado proporcional por segundo dentro de la zona. */
    private static final double SLOW_ZONE_BRAKE = 2.5;
    /** Durante el fantasma, cada rebote destella el disco visible este instante. */
    private static final long GHOST_FLASH_MS = 250;
    /** La zona lenta nunca deja el disco más lento que esto (px/s). */
    private static final double MIN_ZONE_SPEED = 120;
    /** Multiplicadores del tope de velocidad para caos y zona rápida. */
    private static final double CHAOS_CAP = 2.0;
    private static final double FAST_ZONE_CAP = 1.5;

    private final GameProperties props;

    public PhysicsEngine(GameProperties props) {
        this.props = props;
    }

    /**
     * Avanza la partida {@code dt} segundos: integra el disco, rebota en
     * las paredes, revisa goles y resuelve colisiones con las paletas.
     */
    public TickOutcome tick(GameState state, double dt) {
        if (state.getStatus() != GameStatus.PLAYING) {
            return TickOutcome.NONE;
        }
        if (state.getPuckVx() == 0 && state.getPuckVy() == 0) {
            serve(state, ThreadLocalRandom.current().nextBoolean() ? 1 : -1);
        }

        state.setPuckX(state.getPuckX() + state.getPuckVx() * dt);
        state.setPuckY(state.getPuckY() + state.getPuckVy() * dt);

        bounceHorizontalWalls(state);
        var outcome = resolveGoalsAndVerticalWalls(state);
        if (outcome != TickOutcome.NONE) {
            return outcome;
        }

        collideWithObstacles(state);
        collideWithPaddle(state, 1);
        collideWithPaddle(state, 2);
        applyZones(state, dt);
        capSpeed(state);
        return TickOutcome.NONE;
    }

    /**
     * Aplica el input de mouse de un jugador, recortado a su mitad de la
     * cancha y dentro del tablero. El servidor es autoritativo: coordenadas
     * fuera de rango simplemente se ajustan al borde permitido.
     */
    public void movePaddle(GameState state, int playerNumber, double x, double y) {
        double r = props.paddleRadius();
        double half = props.boardWidth() / 2.0;
        double clampedY = clamp(y, r, props.boardHeight() - r);
        if (playerNumber == 1) {
            state.setPaddle1X(clamp(x, r, half - r));
            state.setPaddle1Y(clampedY);
        } else {
            state.setPaddle2X(clamp(x, half + r, props.boardWidth() - r));
            state.setPaddle2Y(clampedY);
        }
    }

    private void bounceHorizontalWalls(GameState state) {
        double r = props.puckRadius();
        if (state.getPuckY() - r < 0) {
            state.setPuckY(r);
            state.setPuckVy(-state.getPuckVy());
            revealPuck(state);
        } else if (state.getPuckY() + r > props.boardHeight()) {
            state.setPuckY(props.boardHeight() - r);
            state.setPuckVy(-state.getPuckVy());
            revealPuck(state);
        }
    }

    /**
     * Paredes izquierda/derecha: rebote, salvo en la abertura de la
     * portería, donde el disco que llega a la línea es gol.
     */
    private TickOutcome resolveGoalsAndVerticalWalls(GameState state) {
        double r = props.puckRadius();
        if (state.getPuckX() - r <= 0) {
            if (inGoalMouth(state.getPuckY())) {
                return goalScored(state, 2);
            }
            state.setPuckX(r);
            state.setPuckVx(-state.getPuckVx());
            revealPuck(state);
        } else if (state.getPuckX() + r >= props.boardWidth()) {
            if (inGoalMouth(state.getPuckY())) {
                return goalScored(state, 1);
            }
            state.setPuckX(props.boardWidth() - r);
            state.setPuckVx(-state.getPuckVx());
            revealPuck(state);
        }
        return TickOutcome.NONE;
    }

    private boolean inGoalMouth(double puckY) {
        double top = (props.boardHeight() - props.goalWidth()) / 2.0;
        return puckY >= top && puckY <= top + props.goalWidth();
    }

    private TickOutcome goalScored(GameState state, int scorer) {
        if (scorer == 1) {
            state.setScore1(state.getScore1() + 1);
        } else {
            state.setScore2(state.getScore2() + 1);
        }
        resetPuckToCenter(state);
        if (state.getScore1() >= props.goalsToWin() || state.getScore2() >= props.goalsToWin()) {
            state.setStatus(GameStatus.FINISHED);
            state.setFinishReason(FinishReason.SCORE);
            state.setWinnerId(state.getScore1() > state.getScore2()
                    ? state.getPlayer1().userId()
                    : state.getPlayer2() == null ? null : state.getPlayer2().userId());
            state.setFinishedAtEpochMs(System.currentTimeMillis());
            return TickOutcome.FINISHED;
        }
        serve(state, scorer == 2 ? -1 : 1);
        return TickOutcome.GOAL;
    }

    private void resetPuckToCenter(GameState state) {
        state.setPuckX(props.boardWidth() / 2.0);
        state.setPuckY(props.boardHeight() / 2.0);
        state.setPuckVx(0);
        state.setPuckVy(0);
        // El gol resetea los efectos sobre el disco: se ve, va a velocidad
        // normal y el fantasma termina (rally nuevo, disco visible).
        state.setPuckVisible(true);
        state.setChaosShot(false);
        state.setGhostUntilEpochMs(0);
        state.setGhostFlashUntilEpochMs(0);
        if (state.getEffects() != null) {
            state.getEffects().removeIf(effect -> effect.type() == PowerType.GHOST_PUCK);
        }
    }

    /** Saque desde donde esté el disco, con ángulo aleatorio de ±30°. */
    private void serve(GameState state, int directionX) {
        double angle = Math.toRadians(ThreadLocalRandom.current().nextDouble(-SERVE_ANGLE, SERVE_ANGLE));
        state.setPuckVx(directionX * props.serveSpeed() * Math.cos(angle));
        state.setPuckVy(props.serveSpeed() * Math.sin(angle));
    }

    private void collideWithPaddle(GameState state, int player) {
        double paddleX = player == 1 ? state.getPaddle1X() : state.getPaddle2X();
        double paddleY = player == 1 ? state.getPaddle1Y() : state.getPaddle2Y();
        double dx = state.getPuckX() - paddleX;
        double dy = state.getPuckY() - paddleY;
        double dist = Math.hypot(dx, dy);
        double minDist = props.puckRadius() + paddleRadius(state, player);
        if (dist >= minDist) {
            return;
        }
        int fallbackNormalX = player == 1 ? 1 : -1;
        double nx = dist == 0 ? fallbackNormalX : dx / dist;
        double ny = dist == 0 ? 0 : dy / dist;

        state.setPuckX(paddleX + nx * minDist);
        state.setPuckY(paddleY + ny * minDist);

        double speed = Math.hypot(state.getPuckVx(), state.getPuckVy());
        double newSpeed = Math.min(Math.max(speed, MIN_HIT_SPEED) + HIT_BOOST, props.maxPuckSpeed());
        if (state.isChaosArmed()) {
            // Caos: este golpe sale al doble y conserva ese tope hasta el
            // próximo golpe o gol (capSpeed respeta el tiro caótico).
            newSpeed = Math.min(newSpeed * 2, props.maxPuckSpeed() * CHAOS_CAP);
            state.setChaosArmed(false);
            state.setChaosShot(true);
        } else {
            state.setChaosShot(false);
        }
        state.setPuckVx(nx * newSpeed);
        state.setPuckVy(ny * newSpeed);
        revealPuck(state);
        keepPuckInsideBoard(state);
    }

    /**
     * Un contacto revela el disco: del todo si no hay fantasma activo, o
     * como un destello brevísimo que insinúa la dirección mientras el
     * fantasma dure (el PowerManager lo vuelve a ocultar al expirar el
     * destello).
     */
    private void revealPuck(GameState state) {
        state.setPuckVisible(true);
        long now = System.currentTimeMillis();
        if (now < state.getGhostUntilEpochMs()) {
            state.setGhostFlashUntilEpochMs(now + GHOST_FLASH_MS);
        }
    }

    /** Radio efectivo de la paleta (el escudo lo dobla); 0 = estado viejo, usa el base. */
    private double paddleRadius(GameState state, int player) {
        double radius = player == 1 ? state.getPaddle1Radius() : state.getPaddle2Radius();
        return radius > 0 ? radius : props.paddleRadius();
    }

    /** Rebote contra los obstáculos activos: círculos estáticos en el tablero. */
    private void collideWithObstacles(GameState state) {
        for (ActiveEffect effect : effects(state)) {
            if (effect.type() != PowerType.OBSTACLE) {
                continue;
            }
            double dx = state.getPuckX() - effect.x();
            double dy = state.getPuckY() - effect.y();
            double dist = Math.hypot(dx, dy);
            double minDist = props.puckRadius() + effect.radius();
            if (dist >= minDist) {
                continue;
            }
            double nx = dist == 0 ? 1 : dx / dist;
            double ny = dist == 0 ? 0 : dy / dist;
            state.setPuckX(effect.x() + nx * minDist);
            state.setPuckY(effect.y() + ny * minDist);
            // Refleja la velocidad sobre la normal solo si va hacia adentro.
            double dot = state.getPuckVx() * nx + state.getPuckVy() * ny;
            if (dot < 0) {
                state.setPuckVx(state.getPuckVx() - 2 * dot * nx);
                state.setPuckVy(state.getPuckVy() - 2 * dot * ny);
            }
            revealPuck(state);
            keepPuckInsideBoard(state);
        }
    }

    /** Zonas rápidas/lentas: modulan la velocidad mientras el disco esté adentro. */
    private void applyZones(GameState state, double dt) {
        for (ActiveEffect effect : effects(state)) {
            boolean inside = Math.hypot(state.getPuckX() - effect.x(),
                    state.getPuckY() - effect.y()) <= effect.radius();
            if (!inside) {
                continue;
            }
            if (effect.type() == PowerType.FAST_ZONE) {
                scaleVelocity(state, 1 + FAST_ZONE_ACCEL * dt);
            } else if (effect.type() == PowerType.SLOW_ZONE) {
                scaleVelocity(state, Math.max(0, 1 - SLOW_ZONE_BRAKE * dt));
                enforceMinZoneSpeed(state);
            }
        }
    }

    private void scaleVelocity(GameState state, double factor) {
        state.setPuckVx(state.getPuckVx() * factor);
        state.setPuckVy(state.getPuckVy() * factor);
    }

    /** La zona lenta frena pero nunca detiene: el disco no puede quedar atrapado. */
    private void enforceMinZoneSpeed(GameState state) {
        double speed = Math.hypot(state.getPuckVx(), state.getPuckVy());
        if (speed > 0 && speed < MIN_ZONE_SPEED) {
            scaleVelocity(state, MIN_ZONE_SPEED / speed);
        }
    }

    private List<ActiveEffect> effects(GameState state) {
        return state.getEffects() == null ? List.of() : state.getEffects();
    }

    /**
     * El empujón de la colisión puede dejar el disco fuera del tablero si la
     * paleta está pegada a una pared o esquina: el disco "desaparecía" y
     * quedaba atrapado en un bucle pared-paleta mientras la paleta no se
     * moviera. Se confina al tablero y la velocidad del eje recortado se
     * orienta hacia adentro para que el disco salga de la trampa. También
     * evita que una paleta meta el disco a la portería empujándolo.
     */
    private void keepPuckInsideBoard(GameState state) {
        double r = props.puckRadius();
        if (state.getPuckX() < r) {
            state.setPuckX(r);
            state.setPuckVx(Math.abs(state.getPuckVx()));
        } else if (state.getPuckX() > props.boardWidth() - r) {
            state.setPuckX(props.boardWidth() - r);
            state.setPuckVx(-Math.abs(state.getPuckVx()));
        }
        if (state.getPuckY() < r) {
            state.setPuckY(r);
            state.setPuckVy(Math.abs(state.getPuckVy()));
        } else if (state.getPuckY() > props.boardHeight() - r) {
            state.setPuckY(props.boardHeight() - r);
            state.setPuckVy(-Math.abs(state.getPuckVy()));
        }
    }

    /**
     * Tope de velocidad dinámico: el tiro caótico permite 2x y estar
     * dentro de una zona rápida 1.5x; el resto del tiempo aplica el
     * máximo normal.
     */
    private void capSpeed(GameState state) {
        double cap = props.maxPuckSpeed();
        if (state.isChaosShot()) {
            cap = props.maxPuckSpeed() * CHAOS_CAP;
        } else if (insideFastZone(state)) {
            cap = props.maxPuckSpeed() * FAST_ZONE_CAP;
        }
        double speed = Math.hypot(state.getPuckVx(), state.getPuckVy());
        if (speed > cap) {
            scaleVelocity(state, cap / speed);
        }
    }

    private boolean insideFastZone(GameState state) {
        for (ActiveEffect effect : effects(state)) {
            if (effect.type() == PowerType.FAST_ZONE
                    && Math.hypot(state.getPuckX() - effect.x(),
                            state.getPuckY() - effect.y()) <= effect.radius()) {
                return true;
            }
        }
        return false;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
