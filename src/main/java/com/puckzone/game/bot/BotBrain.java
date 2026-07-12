package com.puckzone.game.bot;

import com.puckzone.game.bot.BotProfile.PredictionTier;
import com.puckzone.game.config.BotProperties;
import com.puckzone.game.config.GameProperties;
import com.puckzone.game.physics.PhysicsEngine;
import com.puckzone.game.power.PowerPickup;
import com.puckzone.game.room.GameRoomRemovedEvent;
import com.puckzone.game.room.GameState;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * El bot adaptativo (niveles 1-9 según el ELO del rival). Juega con
 * PERCEPCIÓN RETRASADA: solo "mira" el disco cada {@code reactionMillis}
 * de su nivel, y entre miradas actúa sobre la trayectoria vieja — de ahí
 * sale la torpeza de los niveles bajos sin programar torpeza. La misma
 * mecánica lo deja ciego al disco fantasma (puckVisible=false no refresca
 * la percepción): extrapola la última trayectoria conocida, como un
 * humano, y los destellos de los rebotes se la refrescan.
 *
 * <p>Decisión por tick: si el disco está en su mitad, REMATA (se coloca
 * detrás del disco alineado con el arco rival y empuja); si viene hacia
 * él, DEFIENDE en la línea de guardia (los niveles 7-9 calculan el punto
 * de intercepción con rebotes vía {@link PuckPredictor}); si está en la
 * mitad rival, vuelve a la GUARDIA — salvo los niveles 7+, que aprovechan
 * para recoger un pickup activo en su mitad. Toda la puntería lleva un
 * ruido gaussiano estable entre miradas (σ según nivel).
 *
 * <p>Mueve la paleta por el mismo {@code PhysicsEngine.movePaddle} que
 * valida a los humanos, con su velocidad tope por nivel: el bot no puede
 * hacer nada que un jugador no pueda.
 */
@Component
public class BotBrain {

    private final PhysicsEngine engine;
    private final GameProperties props;
    private final BotProperties botProps;
    private final LongSupplier clock;
    /** Percepción y ruido por sala; se limpia cuando la sala sale del mapa. */
    private final Map<String, BotMemory> memories = new ConcurrentHashMap<>();

    @Autowired
    public BotBrain(PhysicsEngine engine, GameProperties props, BotProperties botProps) {
        this(engine, props, botProps, System::currentTimeMillis);
    }

    /** Reloj inyectable para testear la reacción sin dormir hilos. */
    BotBrain(PhysicsEngine engine, GameProperties props, BotProperties botProps,
             LongSupplier clock) {
        this.engine = engine;
        this.props = props;
        this.botProps = botProps;
        this.clock = clock;
    }

    /** Un turno del bot; lo invoca el GameLoop en cada tick de salas vs bot. */
    public void act(GameState state, double dt) {
        BotProfile profile = BotProfile.forLevel(
                state.getBotLevel() > 0 ? state.getBotLevel() : botProps.defaultLevel(), botProps);
        BotMemory mem = memories.computeIfAbsent(state.getGameId(),
                id -> new BotMemory(id.hashCode()));
        long now = clock.getAsLong();

        // Mirada nueva solo si el disco es visible y ya pasó su tiempo de
        // reacción; el fantasma NO refresca (se juega con la trayectoria vieja).
        if (state.isPuckVisible() && now >= mem.nextPerceptionAtMs) {
            mem.puckX = state.getPuckX();
            mem.puckY = state.getPuckY();
            mem.puckVx = state.getPuckVx();
            mem.puckVy = state.getPuckVy();
            mem.perceivedAtMs = now;
            mem.nextPerceptionAtMs = now + profile.reactionMillis();
            mem.aimNoiseY = mem.rng.nextGaussian() * profile.aimErrorPx();
        }

        // Dónde cree el bot que está el disco: los niveles con predicción
        // adelantan la trayectoria percibida el tiempo que llevan sin mirar.
        double px = mem.puckX;
        double py = mem.puckY;
        if (profile.prediction() != PredictionTier.CHASE) {
            double age = (now - mem.perceivedAtMs) / 1000.0;
            px += mem.puckVx * age;
            py = PuckPredictor.fold(py + mem.puckVy * age, minY(), maxY());
        }

        double[] target = chooseTarget(state, profile, mem, px, py, now);
        double maxStep = profile.maxSpeed() * dt;
        engine.movePaddle(state, 2,
                stepTowards(state.getPaddle2X(), target[0], maxStep),
                stepTowards(state.getPaddle2Y(), target[1], maxStep));
    }

    private double[] chooseTarget(GameState state, BotProfile profile, BotMemory mem,
                                  double px, double py, long now) {
        double midX = props.boardWidth() / 2.0;
        double homeX = props.boardWidth() - props.paddleRadius() * 2.0;
        double centerY = props.boardHeight() / 2.0;
        boolean towardBot = mem.puckVx > 0;

        if (px > midX) {
            // REMATE: detrás del disco sobre la línea disco→arco rival, para
            // que el empujón de la colisión lo mande hacia la portería.
            double dx = px; // el arco rival está en x=0, centrado en Y
            double dy = py - centerY;
            double len = Math.hypot(dx, dy);
            if (len < 1e-6) {
                dx = 1;
                len = 1;
            }
            double behind = (props.paddleRadius() + props.puckRadius()) * 0.5;
            return new double[]{px + dx / len * behind, py + dy / len * behind + mem.aimNoiseY};
        }

        if (towardBot) {
            // DEFENSA: esperar el disco sobre la línea de guardia.
            double interceptY = py;
            if (profile.prediction() == PredictionTier.REFLECT) {
                double predicted = PuckPredictor.yAtX(px, py, mem.puckVx, mem.puckVy,
                        homeX, minY(), maxY());
                if (!Double.isNaN(predicted)) {
                    interceptY = predicted;
                }
            }
            return new double[]{homeX, interceptY + mem.aimNoiseY};
        }

        // El disco se aleja en la mitad rival: los niveles altos aprovechan
        // para recoger un pickup ya activo que haya caído en su mitad.
        PowerPickup pickup = state.getPickup();
        if (profile.collectsPowerups() && pickup != null
                && pickup.x() > midX && now >= pickup.activeFromEpochMs()) {
            return new double[]{pickup.x(), pickup.y()};
        }

        // GUARDIA: frente al arco, siguiendo suave la altura del disco.
        return new double[]{homeX, centerY + (py - centerY) * 0.4 + mem.aimNoiseY * 0.5};
    }

    @EventListener
    public void onRoomRemoved(GameRoomRemovedEvent event) {
        memories.remove(event.gameId());
    }

    private double minY() {
        return props.puckRadius();
    }

    private double maxY() {
        return props.boardHeight() - props.puckRadius();
    }

    private static double stepTowards(double current, double target, double maxStep) {
        double delta = target - current;
        if (Math.abs(delta) <= maxStep) {
            return target;
        }
        return current + Math.copySign(maxStep, delta);
    }

    /** Lo que el bot recuerda del disco entre miradas. */
    private static final class BotMemory {
        final Random rng;
        double puckX;
        double puckY;
        double puckVx;
        double puckVy;
        long perceivedAtMs;
        long nextPerceptionAtMs;
        double aimNoiseY;

        BotMemory(long seed) {
            this.rng = new Random(seed);
        }
    }
}
