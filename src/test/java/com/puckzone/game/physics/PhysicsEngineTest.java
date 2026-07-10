package com.puckzone.game.physics;

import com.puckzone.game.config.GameProperties;
import com.puckzone.game.room.GameState;
import com.puckzone.game.room.GameStatus;
import com.puckzone.game.room.OpponentType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sanidad del motor sobre el tablero 800x500: escenarios que se pueden
 * razonar a mano. La portería ocupa y ∈ [150, 350] en cada extremo.
 */
class PhysicsEngineTest {

    private final GameProperties props =
            new GameProperties(800, 500, 7, 60, 200, 15, 30, 900, 300, 30);
    private final PhysicsEngine engine = new PhysicsEngine(props);

    private GameState playing() {
        return GameState.builder()
                .gameId("test")
                .opponentType(OpponentType.HUMAN)
                .status(GameStatus.PLAYING)
                .puckX(400).puckY(250)
                .paddle1X(60).paddle1Y(250)
                .paddle2X(740).paddle2Y(250)
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
        assertEquals(0, state.getPuckVx());      // disco quieto: ya no hay saque
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
