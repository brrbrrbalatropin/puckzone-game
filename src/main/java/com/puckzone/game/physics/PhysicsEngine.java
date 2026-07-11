package com.puckzone.game.physics;

import com.puckzone.game.config.GameProperties;
import com.puckzone.game.room.FinishReason;
import com.puckzone.game.room.GameState;
import com.puckzone.game.room.GameStatus;
import org.springframework.stereotype.Component;

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

        collideWithPaddle(state, state.getPaddle1X(), state.getPaddle1Y(), 1);
        collideWithPaddle(state, state.getPaddle2X(), state.getPaddle2Y(), -1);
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
        } else if (state.getPuckY() + r > props.boardHeight()) {
            state.setPuckY(props.boardHeight() - r);
            state.setPuckVy(-state.getPuckVy());
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
        } else if (state.getPuckX() + r >= props.boardWidth()) {
            if (inGoalMouth(state.getPuckY())) {
                return goalScored(state, 1);
            }
            state.setPuckX(props.boardWidth() - r);
            state.setPuckVx(-state.getPuckVx());
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
    }

    /** Saque desde donde esté el disco, con ángulo aleatorio de ±30°. */
    private void serve(GameState state, int directionX) {
        double angle = Math.toRadians(ThreadLocalRandom.current().nextDouble(-SERVE_ANGLE, SERVE_ANGLE));
        state.setPuckVx(directionX * props.serveSpeed() * Math.cos(angle));
        state.setPuckVy(props.serveSpeed() * Math.sin(angle));
    }

    private void collideWithPaddle(GameState state, double paddleX, double paddleY, int fallbackNormalX) {
        double dx = state.getPuckX() - paddleX;
        double dy = state.getPuckY() - paddleY;
        double dist = Math.hypot(dx, dy);
        double minDist = props.puckRadius() + props.paddleRadius();
        if (dist >= minDist) {
            return;
        }
        double nx = dist == 0 ? fallbackNormalX : dx / dist;
        double ny = dist == 0 ? 0 : dy / dist;

        state.setPuckX(paddleX + nx * minDist);
        state.setPuckY(paddleY + ny * minDist);

        double speed = Math.hypot(state.getPuckVx(), state.getPuckVy());
        double newSpeed = Math.min(Math.max(speed, MIN_HIT_SPEED) + HIT_BOOST, props.maxPuckSpeed());
        state.setPuckVx(nx * newSpeed);
        state.setPuckVy(ny * newSpeed);
        keepPuckInsideBoard(state);
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

    private void capSpeed(GameState state) {
        double speed = Math.hypot(state.getPuckVx(), state.getPuckVy());
        if (speed > props.maxPuckSpeed()) {
            double factor = props.maxPuckSpeed() / speed;
            state.setPuckVx(state.getPuckVx() * factor);
            state.setPuckVy(state.getPuckVy() * factor);
        }
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
