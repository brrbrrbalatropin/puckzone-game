package com.puckzone.game.bot;

import com.puckzone.game.config.GameProperties;
import com.puckzone.game.physics.PhysicsEngine;
import com.puckzone.game.room.GameState;
import org.springframework.stereotype.Component;

/**
 * Bot placeholder: controla la paleta 2 persiguiendo el disco a velocidad
 * limitada, sin predicción de trayectoria. Su "torpeza" sale de esa
 * limitación: ante rebotes rápidos siempre llega tarde. Será reemplazado
 * por el bot adaptativo por niveles (1-9 según ELO) del paquete bot real.
 *
 * <p>Mueve la paleta por el mismo {@code PhysicsEngine.movePaddle} que
 * valida a los humanos: el bot no puede hacer nada que un jugador no pueda.
 */
@Component
public class BotPaddle {

    /** Velocidad máxima de la paleta del bot (px/s). Vencible: el saque va a 300. */
    private static final double SPEED = 220;

    private final PhysicsEngine engine;
    private final GameProperties props;

    public BotPaddle(PhysicsEngine engine, GameProperties props) {
        this.engine = engine;
        this.props = props;
    }

    /**
     * Un "turno" del bot: persigue la altura del disco siempre, y su X
     * solo cuando el disco está en su mitad; si no, vuelve a la guardia
     * frente a su portería.
     */
    public void act(GameState state, double dt) {
        double homeX = props.boardWidth() - props.paddleRadius() * 2.0;
        boolean puckInBotHalf = state.getPuckX() > props.boardWidth() / 2.0;

        double targetX = puckInBotHalf ? state.getPuckX() : homeX;
        double targetY = state.getPuckY();

        double maxStep = SPEED * dt;
        double newX = stepTowards(state.getPaddle2X(), targetX, maxStep);
        double newY = stepTowards(state.getPaddle2Y(), targetY, maxStep);

        engine.movePaddle(state, 2, newX, newY);
    }

    private static double stepTowards(double current, double target, double maxStep) {
        double delta = target - current;
        if (Math.abs(delta) <= maxStep) {
            return target;
        }
        return current + Math.copySign(maxStep, delta);
    }
}
