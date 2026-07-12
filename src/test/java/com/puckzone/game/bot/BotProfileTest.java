package com.puckzone.game.bot;

import com.puckzone.game.bot.BotProfile.PredictionTier;
import com.puckzone.game.config.BotProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * El mapeo ELO→nivel (bandas de 150 desde 900) y la interpolación de las
 * perillas: a más nivel, más rápido, más preciso y de reacción más corta.
 */
class BotProfileTest {

    private static final BotProperties PROPS =
            new BotProperties(4, 120, 220, 550, 350, 80, 30, 45, 6, 3);

    @Test
    void elMapeoDeEloANivelUsaBandasDe150() {
        assertEquals(1, BotProfile.levelForElo(600));
        assertEquals(1, BotProfile.levelForElo(899));
        assertEquals(2, BotProfile.levelForElo(900));
        assertEquals(4, BotProfile.levelForElo(1200), "el jugador nuevo juega contra nivel 4");
        assertEquals(9, BotProfile.levelForElo(1950));
        assertEquals(9, BotProfile.levelForElo(2600), "el nivel tope es 9");
    }

    @Test
    void sinRatingSeAsumeElEloInicial() {
        assertEquals(4, BotProfile.levelForElo(null));
    }

    @Test
    void lasAnclasUsanLasPerillasConfiguradasYElNivelSeAcota() {
        BotProfile l1 = BotProfile.forLevel(1, PROPS);
        BotProfile l9 = BotProfile.forLevel(9, PROPS);
        assertEquals(120, l1.maxSpeed());
        assertEquals(350, l1.reactionMillis());
        assertEquals(550, l9.maxSpeed());
        assertEquals(3, l9.aimErrorPx());
        assertEquals(1, BotProfile.forLevel(-3, PROPS).level());
        assertEquals(9, BotProfile.forLevel(42, PROPS).level());
    }

    @Test
    void elNivel4EsElBotClasicoQueSoloSigueElDisco() {
        // El ancla del medio: persecución pura a ~220 px/s, el bot que
        // validaron los primeros jugadores (rival de la banda 1200-1349).
        BotProfile l4 = BotProfile.forLevel(4, PROPS);
        assertEquals(220, l4.maxSpeed());
        assertEquals(PredictionTier.CHASE, l4.prediction());
        assertFalse(l4.collectsPowerups());
    }

    @Test
    void aMasNivelMasRapidoMasPrecisoYReaccionMasCorta() {
        for (int level = 2; level <= 9; level++) {
            BotProfile lower = BotProfile.forLevel(level - 1, PROPS);
            BotProfile higher = BotProfile.forLevel(level, PROPS);
            assertTrue(higher.maxSpeed() > lower.maxSpeed());
            assertTrue(higher.aimErrorPx() < lower.aimErrorPx());
            assertTrue(higher.reactionMillis() < lower.reactionMillis());
        }
    }

    @Test
    void losTramosDePrediccionYLosPickupsVanPorNivel() {
        assertEquals(PredictionTier.CHASE, BotProfile.forLevel(4, PROPS).prediction());
        assertEquals(PredictionTier.LINEAR, BotProfile.forLevel(5, PROPS).prediction());
        assertEquals(PredictionTier.LINEAR, BotProfile.forLevel(6, PROPS).prediction());
        assertEquals(PredictionTier.REFLECT, BotProfile.forLevel(7, PROPS).prediction());
        assertFalse(BotProfile.forLevel(6, PROPS).collectsPowerups());
        assertTrue(BotProfile.forLevel(7, PROPS).collectsPowerups());
    }
}
