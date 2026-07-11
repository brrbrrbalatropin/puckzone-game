package com.puckzone.game.physics;

import com.puckzone.game.config.GameProperties;
import com.puckzone.game.power.ActiveEffect;
import com.puckzone.game.power.PowerType;
import com.puckzone.game.room.FinishReason;
import com.puckzone.game.room.GameState;
import com.puckzone.game.room.GameStatus;
import com.puckzone.game.room.OpponentType;
import com.puckzone.game.room.Player;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sanidad del motor sobre el tablero 800x500: escenarios que se pueden
 * razonar a mano. La portería ocupa y ∈ [150, 350] en cada extremo.
 */
class PhysicsEngineTest {

    private final GameProperties props =
            new GameProperties(800, 500, 7, 60, 200, 15, 30, 900, 300, 30, 60, 300);
    private final PhysicsEngine engine = new PhysicsEngine(props);

    private GameState playing() {
        return GameState.builder()
                .gameId("test")
                .player1(new Player("p1", "daniel", "escuelaing"))
                .player2(new Player("p2", "rival", "unal"))
                .opponentType(OpponentType.HUMAN)
                .status(GameStatus.PLAYING)
                .puckX(400).puckY(250)
                .paddle1X(60).paddle1Y(250)
                .paddle2X(740).paddle2Y(250)
                .paddle1Radius(30).paddle2Radius(30)
                .puckVisible(true)
                .effects(new ArrayList<>())
                .build();
    }

    @Test
    void elDiscoAvanzaSegunSuVelocidad() {
        var state = playing();
        state.setPuckVx(120);
        state.setPuckVy(-60);

        engine.tick(state, 0.5);

        assertEquals(460, state.getPuckX());
        assertEquals(220, state.getPuckY());
    }

    @Test
    void rebotaEnLaParedSuperiorInvirtiendoVy() {
        var state = playing();
        state.setPuckY(20);
        state.setPuckVx(50);
        state.setPuckVy(-200);

        engine.tick(state, 0.1);

        assertEquals(15, state.getPuckY());      // queda apoyado en el radio
        assertEquals(200, state.getPuckVy());    // vy invertida
    }

    @Test
    void discoEnLaPorteriaIzquierdaEsGolDelJugador2YSacaHaciaElQueConcedio() {
        var state = playing();
        state.setPuckX(20);
        state.setPuckY(250);                     // dentro de la abertura [150, 350]
        state.setPuckVx(-300);
        state.setPuckVy(0);

        var outcome = engine.tick(state, 0.1);

        assertEquals(TickOutcome.GOAL, outcome);
        assertEquals(1, state.getScore2());
        assertEquals(400, state.getPuckX());     // saque desde el centro
        assertTrue(state.getPuckVx() < 0);       // hacia el jugador 1, que concedió
    }

    @Test
    void fueraDeLaAberturaNoHayGolSinoRebote() {
        var state = playing();
        state.setPuckX(20);
        state.setPuckY(100);                     // por encima de la abertura
        state.setPuckVx(-300);
        state.setPuckVy(0);

        var outcome = engine.tick(state, 0.1);

        assertEquals(TickOutcome.NONE, outcome);
        assertEquals(0, state.getScore2());
        assertEquals(300, state.getPuckVx());    // rebotó
    }

    @Test
    void elGolNumeroSieteTerminaLaPartida() {
        var state = playing();
        state.setScore1(6);
        state.setPuckX(780);
        state.setPuckY(250);
        state.setPuckVx(300);
        state.setPuckVy(0);

        var outcome = engine.tick(state, 0.1);

        assertEquals(TickOutcome.FINISHED, outcome);
        assertEquals(7, state.getScore1());
        assertEquals(GameStatus.FINISHED, state.getStatus());
        assertEquals("p1", state.getWinnerId());
        assertEquals(FinishReason.SCORE, state.getFinishReason());
        assertEquals(0, state.getPuckVx());      // disco quieto: ya no hay saque
    }

    @Test
    void elDiscoRebotaEnElObstaculo() {
        var state = playing();
        state.getEffects().add(new ActiveEffect(PowerType.OBSTACLE, 1, 400, 250, 35,
                System.currentTimeMillis() + 8000));
        state.setPuckX(340);
        state.setPuckY(250);
        state.setPuckVx(300);
        state.setPuckVy(0);

        engine.tick(state, 0.1); // avanza a x=370: penetra el obstáculo (radio 35+15)

        assertTrue(state.getPuckVx() < 0, "el disco no rebotó contra el obstáculo");
        assertEquals(350, state.getPuckX(), 0.001, "debía quedar empujado justo al borde");
    }

    @Test
    void sinFantasmaActivoElReboteRevelaDelTodo() {
        var state = playing();
        state.setPuckVisible(false); // estado heredado, sin fantasma corriendo
        state.setPuckY(20);
        state.setPuckVx(50);
        state.setPuckVy(-200);

        engine.tick(state, 0.1); // rebota en la pared superior

        assertTrue(state.isPuckVisible(), "el rebote debía revelar el disco");
        assertEquals(0, state.getGhostFlashUntilEpochMs(), "sin fantasma no hay destello que armar");
    }

    @Test
    void duranteElFantasmaElReboteSoloDestellaElDisco() {
        var state = playing();
        state.setGhostUntilEpochMs(System.currentTimeMillis() + 10_000);
        state.setPuckVisible(false);
        state.setPuckY(20);
        state.setPuckVx(50);
        state.setPuckVy(-200);

        engine.tick(state, 0.1); // rebota en la pared superior

        assertTrue(state.isPuckVisible(), "el rebote debía destellar el disco");
        assertTrue(state.getGhostFlashUntilEpochMs() > 0,
                "el destello debía quedar armado para que el PowerManager lo apague");
    }

    @Test
    void elGolpeConCaosSaleAlDobleYSeDesarma() {
        var state = playing();
        state.setChaosArmed(true);
        state.setPuckX(100); // a 40 del centro de la paleta 1: dentro de 15+30
        state.setPuckVy(0.001); // casi quieto sin disparar el saque automático

        engine.tick(state, 0.001);

        double speed = Math.hypot(state.getPuckVx(), state.getPuckVy());
        assertEquals(820, speed, 1, "el golpe caótico debía salir al doble del normal (410)");
        assertFalse(state.isChaosArmed(), "el caos es de un solo golpe");
        assertTrue(state.isChaosShot(), "el tiro caótico debe conservar su tope 2x");
    }

    @Test
    void elEscudoDuplicaElAlcanceDeLaPaleta() {
        var normal = playing();
        normal.setPuckX(130); // a 70 de la paleta 1: fuera del alcance normal (45)
        normal.setPuckVy(0.001);
        engine.tick(normal, 0.001);
        assertEquals(0, normal.getPuckVx(), 0.001, "sin escudo no debía haber colisión");

        var shielded = playing();
        shielded.setPaddle1Radius(60); // escudo: alcance 15+60=75 > 70
        shielded.setPuckX(130);
        shielded.setPuckVy(0.001);
        engine.tick(shielded, 0.001);
        assertTrue(shielded.getPuckVx() > 0, "con escudo la paleta agrandada debía golpear");
    }

    @Test
    void laZonaLentaFrenaYLaRapidaAcelera() {
        var slow = playing();
        slow.getEffects().add(new ActiveEffect(PowerType.SLOW_ZONE, 1, 400, 250, 80,
                System.currentTimeMillis() + 8000));
        slow.setPuckVx(300);
        engine.tick(slow, 0.1);
        assertTrue(Math.hypot(slow.getPuckVx(), slow.getPuckVy()) < 300,
                "la zona lenta no frenó el disco");

        var fast = playing();
        fast.getEffects().add(new ActiveEffect(PowerType.FAST_ZONE, 1, 400, 250, 80,
                System.currentTimeMillis() + 8000));
        fast.setPuckVx(300);
        engine.tick(fast, 0.1);
        assertTrue(Math.hypot(fast.getPuckVx(), fast.getPuckVy()) > 300,
                "la zona rápida no aceleró el disco");
    }

    @Test
    void elEmpujonDeLaPaletaEnLaEsquinaNoSacaElDiscoDelTablero() {
        var state = playing();
        // Caso real: el bot incrustado en la esquina superior derecha empujaba
        // el disco fuera del tablero y quedaba "desaparecido" en un bucle
        // pared-paleta. El disco debe quedar confinado y salir hacia adentro.
        state.setPaddle2X(770);
        state.setPaddle2Y(30);
        state.setPuckX(788);
        state.setPuckY(14);
        state.setPuckVx(10);
        state.setPuckVy(0);

        engine.tick(state, 1.0 / 60);

        assertTrue(state.getPuckX() >= 15 && state.getPuckX() <= 785, "x fuera del tablero");
        assertTrue(state.getPuckY() >= 15 && state.getPuckY() <= 485, "y fuera del tablero");
        assertTrue(state.getPuckVx() < 0, "vx debe apuntar hacia adentro");
        assertTrue(state.getPuckVy() > 0, "vy debe apuntar hacia adentro");
    }

    @Test
    void laPaletaSeRecortaASuMitadDeCancha() {
        var state = playing();

        engine.movePaddle(state, 1, 700, 9999);  // intenta invadir la mitad rival

        assertEquals(370, state.getPaddle1X());  // 400 - radio de paleta
        assertEquals(470, state.getPaddle1Y());  // alto - radio
    }
}
