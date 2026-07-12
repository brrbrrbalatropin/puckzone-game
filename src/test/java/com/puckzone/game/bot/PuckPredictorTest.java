package com.puckzone.game.bot;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * La predicción del punto de intercepción: línea recta, uno y dos rebotes
 * (plegado espejo en las paredes), y NaN cuando el disco no viene.
 */
class PuckPredictorTest {

    private static final double MIN_Y = 15;
    private static final double MAX_Y = 485;

    @Test
    void enLineaRectaEsGeometriaSimple() {
        // De (400,200) a 100 px/s en X y 50 en Y: en 3s cruza x=700 a y=350.
        assertEquals(350, PuckPredictor.yAtX(400, 200, 100, 50, 700, MIN_Y, MAX_Y), 1e-9);
    }

    @Test
    void unReboteEnLaParedInferiorSeEspeja() {
        // Sin pared llegaría a y=600; el espejo sobre maxY=485 lo deja en 370.
        assertEquals(370, PuckPredictor.yAtX(400, 300, 100, 100, 700, MIN_Y, MAX_Y), 1e-9);
    }

    @Test
    void dosRebotesTambienSePliegan() {
        // Sin paredes: y = 200 + 400*3 = 1400. Plegado: periodo 940,
        // offset 1400-15=1385 > 940 → 1385-940=445 <= 470 → y=460.
        assertEquals(460, PuckPredictor.yAtX(100, 200, 100, 400, 400, MIN_Y, MAX_Y), 1e-9);
    }

    @Test
    void siElDiscoNoVieneHaciaLaLineaEsNaN() {
        assertTrue(Double.isNaN(PuckPredictor.yAtX(400, 200, -100, 50, 700, MIN_Y, MAX_Y)),
                "se aleja");
        assertTrue(Double.isNaN(PuckPredictor.yAtX(400, 200, 0, 50, 700, MIN_Y, MAX_Y)),
                "va vertical");
    }

    @Test
    void elPlegadoRespetaLosBordes() {
        assertEquals(MIN_Y, PuckPredictor.fold(MIN_Y, MIN_Y, MAX_Y), 1e-9);
        assertEquals(MAX_Y, PuckPredictor.fold(MAX_Y, MIN_Y, MAX_Y), 1e-9);
        // Muy por debajo de minY también se espeja hacia adentro.
        assertEquals(115, PuckPredictor.fold(-85, MIN_Y, MAX_Y), 1e-9);
    }
}
