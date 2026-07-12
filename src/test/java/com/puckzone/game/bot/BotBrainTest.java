package com.puckzone.game.bot;

import com.puckzone.game.config.BotProperties;
import com.puckzone.game.config.GameProperties;
import com.puckzone.game.physics.PhysicsEngine;
import com.puckzone.game.power.PowerPickup;
import com.puckzone.game.power.PowerType;
import com.puckzone.game.room.GameState;
import com.puckzone.game.room.GameStatus;
import com.puckzone.game.room.OpponentType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * El cerebro del bot con reloj falso: respeta su velocidad tope, defiende
 * en el punto de intercepción predicho, no reacciona antes de su tiempo de
 * reacción, queda ciego al disco fantasma (juega la trayectoria vieja) y
 * los niveles altos desvían a recoger pickups.
 */
class BotBrainTest {

    private static final double DT = 1.0 / 60;

    private final GameProperties props =
            new GameProperties(800, 500, 7, 60, 200, 15, 30, 900, 300, 30, 2, 60, 300);
    private final BotProperties botProps =
            new BotProperties(4, 120, 220, 550, 350, 80, 30, 45, 6, 3);

    private final long[] now = {1_000_000L};
    private final BotBrain bot =
            new BotBrain(new PhysicsEngine(props), props, botProps, () -> now[0]);

    private GameState game(int level) {
        return GameState.builder()
                .gameId("sala-bot")
                .status(GameStatus.PLAYING)
                .opponentType(OpponentType.BOT)
                .botLevel(level)
                .puckX(400).puckY(250)
                .paddle1X(60).paddle1Y(250)
                .paddle2X(740).paddle2Y(250)
                .paddle1Radius(30).paddle2Radius(30)
                .puckVisible(true)
                .effects(new ArrayList<>())
                .build();
    }

    private void actTimes(GameState state, int times) {
        for (int i = 0; i < times; i++) {
            bot.act(state, DT);
        }
    }

    @Test
    void nuncaSeMueveMasRapidoQueSuNivel() {
        GameState state = game(1); // 120 px/s
        state.setPuckY(30); // objetivo lejos
        double beforeY = state.getPaddle2Y();

        bot.act(state, DT);

        double step = Math.abs(state.getPaddle2Y() - beforeY);
        assertTrue(step <= 120 * DT + 1e-6, "se movió " + step + "px en un tick de nivel 1");
    }

    @Test
    void elNivelAltoDefiendeEnElPuntoDeIntercepcionConRebote() {
        GameState state = game(9);
        // Disco en (390,300) hacia el bot: sin pared cruzaría la guardia
        // (x=740) en y=300+250*3.5=1175; plegado en [15,485] → 705-235...
        state.setPuckX(390);
        state.setPuckY(300);
        state.setPuckVx(100);
        state.setPuckVy(250);

        actTimes(state, 200); // tiempo de sobra para plantarse

        double expectedY = PuckPredictor.yAtX(390, 300, 100, 250, 740, 15, 485);
        assertEquals(740, state.getPaddle2X(), 30, "espera sobre la línea de guardia");
        assertEquals(expectedY, state.getPaddle2Y(), 25, "en el punto de intercepción predicho");
    }

    @Test
    void noReaccionaAntesDeSuTiempoDeReaccion() {
        GameState state = game(7); // reacción 50ms (rampa 80→30 entre 4 y 9)
        state.setPuckVx(100); // hacia el bot, y constante en 250
        actTimes(state, 5); // percibe la trayectoria original

        // El disco cambia de rumbo, pero el bot aún no vuelve a mirar.
        state.setPuckY(60);
        now[0] += 30; // < 50ms
        actTimes(state, 3);
        assertEquals(250, state.getPaddle2Y(), 30, "sigue plantado en la trayectoria vieja");

        // Pasado su tiempo de reacción, la mirada nueva lo corrige.
        now[0] += 40; // ya van 70ms
        actTimes(state, 200);
        assertEquals(60, state.getPaddle2Y(), 30, "ahora sí persigue el rumbo nuevo");
    }

    @Test
    void quedaCiegoAlDiscoFantasma() {
        GameState state = game(9);
        state.setPuckVx(100); // hacia el bot por el centro
        actTimes(state, 5);

        // El disco se vuelve fantasma y se teletransporta abajo del todo.
        state.setPuckVisible(false);
        state.setPuckY(470);
        now[0] += 500; // mucho más que sus 50ms de reacción
        actTimes(state, 100);
        assertTrue(Math.abs(state.getPaddle2Y() - 470) > 100,
                "no debe saber que el disco está en 470: era invisible");

        // El destello de un rebote (visible de nuevo) le refresca la percepción.
        state.setPuckVisible(true);
        now[0] += 100;
        actTimes(state, 200);
        assertEquals(470, state.getPaddle2Y(), 30, "con el destello ya lo vio");
    }

    @Test
    void elNivelAltoDesviaARecogerElPickupYElBajoNo() {
        long activeSince = now[0] - 1;
        PowerPickup pickup = new PowerPickup(PowerType.SHIELD, 600, 420, activeSince, now[0] + 60_000);

        // Disco alejándose en la mitad rival: momento de ir por el pickup.
        GameState high = game(9);
        high.setPuckX(200);
        high.setPuckVx(-100);
        high.setPickup(pickup);
        actTimes(high, 200);
        assertEquals(600, high.getPaddle2X(), 25, "el nivel 9 va por el pickup");
        assertEquals(420, high.getPaddle2Y(), 25);

        GameState low = game(4);
        low.setPuckX(200);
        low.setPuckVx(-100);
        low.setPickup(pickup);
        actTimes(low, 200);
        assertEquals(740, low.getPaddle2X(), 30, "el nivel 4 se queda en guardia");
    }

    @Test
    void conElDiscoEnSuMitadSeColocaDetrasParaRematarHaciaElArco() {
        GameState state = game(9);
        state.setPuckX(600);
        state.setPuckY(250); // alineado con el arco rival: detrás = más a la derecha
        state.setPuckVx(-20);

        actTimes(state, 200);

        assertTrue(state.getPaddle2X() > 600,
                "debe quedar DETRÁS del disco (entre el disco y su propio arco)");
        assertEquals(250, state.getPaddle2Y(), 25, "alineado con la línea disco→arco");
    }
}
