package com.puckzone.game.bot;

import com.puckzone.game.config.GameProperties;
import com.puckzone.game.physics.PhysicsEngine;
import com.puckzone.game.room.GameState;
import com.puckzone.game.room.GameStatus;
import com.puckzone.game.room.OpponentType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Comportamiento del bot placeholder sobre el tablero 800x500: persigue
 * a velocidad limitada, vuelve a la guardia y nunca cruza a la mitad rival.
 */
class BotPaddleTest {

    private final GameProperties props =
            new GameProperties(800, 500, 7, 60, 200, 15, 30, 900, 300);
    private final BotPaddle bot = new BotPaddle(new PhysicsEngine(props), props);

    private GameState game() {
        return GameState.builder()
                .gameId("test-bot")
                .opponentType(OpponentType.BOT)
                .status(GameStatus.PLAYING)
                .puckX(400).puckY(250)
                .paddle1X(60).paddle1Y(250)
                .paddle2X(740).paddle2Y(250)
                .build();
    }

    @Test
    void persigueElDiscoSinTeletransportarse() {
        var state = game();
        state.setPuckX(700);
        state.setPuckY(100);   // muy por encima de la paleta (740, 250)

        bot.act(state, 0.1);   // puede moverse máximo 220 * 0.1 = 22 px por eje

        assertEquals(718, state.getPaddle2X());  // 740 → 700, avanzó 22
        assertEquals(228, state.getPaddle2Y());  // 250 → 100, avanzó 22
    }

    @Test
    void vuelveALaGuardiaCuandoElDiscoEstaEnLaMitadRival() {
        var state = game();
        state.setPuckX(100);   // disco en la mitad del jugador 1
        state.setPuckY(250);
        state.setPaddle2X(500);

        bot.act(state, 0.1);

        assertEquals(522, state.getPaddle2X()); // camina de vuelta hacia 740
    }

    @Test
    void nuncaInvadeLaMitadRival() {
        var state = game();
        state.setPuckX(405);   // apenas en su mitad, pegado a la línea
        state.setPuckY(250);
        state.setPaddle2X(432);

        bot.act(state, 0.1);   // querría llegar a 405, pero 430 es el mínimo legal

        assertTrue(state.getPaddle2X() >= 430, "la paleta cruzó la mitad: " + state.getPaddle2X());
    }
}
