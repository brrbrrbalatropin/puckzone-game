package com.puckzone.game.power;

import com.puckzone.game.config.GameProperties;
import com.puckzone.game.config.PowerProperties;
import com.puckzone.game.room.GameState;
import com.puckzone.game.room.GameStatus;
import com.puckzone.game.room.OpponentType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ciclo de vida de los poderes con el reloj controlado por el test:
 * aparición con parpadeo, recogida por contacto de paleta con efecto
 * inmediato según el tipo, expiración del pickup ignorado y del escudo.
 */
class PowerManagerTest {

    private static final long T0 = 1_000_000;

    private final GameProperties gameProps =
            new GameProperties(800, 500, 7, 60, 200, 15, 30, 900, 300, 30, 2, 60, 300);
    /** Spawn cada 12s, parpadeo 2s, vida 10s, efectos de 8s, fantasma 10-15s. */
    private final PowerProperties props = new PowerProperties(12, 2, 10, 8, 18, 35, 80, 10, 15);
    private final PowerManager manager = new PowerManager(props, gameProps);

    private GameState playing() {
        return GameState.builder()
                .gameId("test")
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

    /** Pickup ya recogible (parpadeo cumplido) en la posición dada. */
    private PowerPickup collectible(PowerType type, double x, double y) {
        return new PowerPickup(type, x, y, T0 - 1, T0 + 10_000);
    }

    @Test
    void elPickupApareceTrasElIntervaloYArrancaParpadeando() {
        var state = playing();

        manager.tick(state, T0); // primer tick: solo arma el reloj
        assertNull(state.getPickup());
        manager.tick(state, T0 + 11_999);
        assertNull(state.getPickup(), "apareció antes del intervalo");
        manager.tick(state, T0 + 12_000);

        PowerPickup pickup = state.getPickup();
        assertNotNull(pickup, "no apareció cumplido el intervalo");
        assertEquals(T0 + 14_000, pickup.activeFromEpochMs(), "el parpadeo debe durar 2s");
        assertTrue(pickup.x() >= 120 && pickup.x() <= 680, "x fuera del área jugable");
        assertTrue(pickup.y() >= 75 && pickup.y() <= 425, "y fuera del área jugable");
    }

    @Test
    void duranteElParpadeoNoSePuedeRecoger() {
        var state = playing();
        state.setPickup(new PowerPickup(PowerType.SHIELD, 60, 250, T0 + 1_000, T0 + 11_000));

        manager.tick(state, T0); // la paleta 1 está encima, pero aún parpadea

        assertNotNull(state.getPickup(), "se recogió durante el parpadeo");
        assertEquals(30, state.getPaddle1Radius(), 0.001);
    }

    @Test
    void recogerElEscudoDuplicaLaPaletaDelQueLoToca() {
        var state = playing();
        state.setPickup(collectible(PowerType.SHIELD, 60, 250));

        manager.tick(state, T0);

        assertNull(state.getPickup());
        assertEquals(60, state.getPaddle1Radius(), 0.001, "el escudo debía doblar la paleta 1");
        assertEquals(30, state.getPaddle2Radius(), 0.001, "la paleta 2 no debía cambiar");
        assertEquals(1, state.getEffects().size());
        assertEquals(PowerType.SHIELD, state.getEffects().get(0).type());
        assertEquals(T0 + 8_000, state.getEffects().get(0).expiresAtEpochMs());
    }

    @Test
    void recogerElFantasmaOcultaElDiscoPorUnaDuracionAleatoria() {
        var state = playing();
        state.setPickup(collectible(PowerType.GHOST_PUCK, 740, 250)); // lo toca la paleta 2

        manager.tick(state, T0);

        assertFalse(state.isPuckVisible(), "el disco debía quedar invisible");
        assertTrue(state.getGhostUntilEpochMs() >= T0 + 10_000
                        && state.getGhostUntilEpochMs() <= T0 + 15_000,
                "la duración debía caer en el rango aleatorio 10-15s");
        assertEquals(PowerType.GHOST_PUCK, state.getEffects().get(0).type(),
                "falta la entrada para el badge del HUD");
    }

    @Test
    void elDestelloDelReboteSeApagaSoloYElDiscoVuelveAOcultarse() {
        var state = playing();
        state.setGhostUntilEpochMs(T0 + 10_000);
        state.setGhostFlashUntilEpochMs(T0 + 250); // un rebote acaba de destellar
        state.setPuckVisible(true);

        manager.tick(state, T0 + 100);
        assertTrue(state.isPuckVisible(), "el destello debía seguir encendido");

        manager.tick(state, T0 + 300);
        assertFalse(state.isPuckVisible(), "pasado el destello debía volver a ocultarse");
    }

    @Test
    void alVencerLaDuracionElDiscoVuelveASerVisible() {
        var state = playing();
        state.setGhostUntilEpochMs(T0 + 5_000);
        state.setPuckVisible(false);

        manager.tick(state, T0 + 5_000);

        assertTrue(state.isPuckVisible(), "vencido el fantasma el disco debía reaparecer");
        assertEquals(0, state.getGhostUntilEpochMs());
    }

    @Test
    void recogerElCaosArmaElProximoGolpe() {
        var state = playing();
        state.setPickup(collectible(PowerType.CHAOS, 60, 250));

        manager.tick(state, T0);

        assertTrue(state.isChaosArmed());
    }

    @Test
    void elObstaculoQuedaAncladoDondeEstabaElPickup() {
        var state = playing();
        state.setPickup(collectible(PowerType.OBSTACLE, 80, 260)); // al alcance de la paleta 1

        manager.tick(state, T0);

        var effect = state.getEffects().get(0);
        assertEquals(PowerType.OBSTACLE, effect.type());
        assertEquals(80, effect.x(), 0.001);
        assertEquals(260, effect.y(), 0.001);
        assertEquals(35, effect.radius(), 0.001);
    }

    @Test
    void elPickupQueNadieTocaExpira() {
        var state = playing();
        state.setPickup(new PowerPickup(PowerType.CHAOS, 400, 100, T0 - 11_000, T0 - 1));

        manager.tick(state, T0);

        assertNull(state.getPickup(), "el pickup ignorado debía desaparecer");
        assertTrue(state.getEffects().isEmpty());
        assertFalse(state.isChaosArmed());
    }

    @Test
    void lejosDeLasPaletasNadieLoRecoge() {
        var state = playing();
        state.setPickup(collectible(PowerType.CHAOS, 400, 100)); // lejos de ambas

        manager.tick(state, T0);

        assertNotNull(state.getPickup(), "nadie lo tocó: debía seguir en el tablero");
        assertFalse(state.isChaosArmed());
    }

    @Test
    void elEscudoExpiradoRestauraElRadioDeLaPaleta() {
        var state = playing();
        state.setPaddle1Radius(60);
        state.getEffects().add(new ActiveEffect(PowerType.SHIELD, 1, 0, 0, 0, T0 - 1));

        manager.tick(state, T0);

        assertEquals(30, state.getPaddle1Radius(), 0.001, "el escudo vencido no restauró el radio");
        assertTrue(state.getEffects().isEmpty());
    }
}
