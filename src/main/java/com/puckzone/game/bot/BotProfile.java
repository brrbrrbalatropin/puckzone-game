package com.puckzone.game.bot;

import com.puckzone.game.config.BotProperties;

/**
 * Parámetros de juego de un nivel del bot (1-9). Un solo cerebro con
 * distintas limitaciones: los niveles bajos no son "más tontos", son más
 * lentos, más imprecisos y tardan más en reaccionar — como un humano
 * novato. Los valores intermedios se interpolan entre los extremos
 * configurados en {@link BotProperties}.
 */
public record BotProfile(
        int level,
        long reactionMillis,
        double maxSpeed,
        double aimErrorPx,
        PredictionTier prediction,
        boolean collectsPowerups
) {

    /** Qué tan lejos "ve" el bot la trayectoria del disco. */
    public enum PredictionTier {
        /**
         * Persigue el disco donde está (niveles 1-4). El nivel 4 es el
         * bot clásico validado por los primeros jugadores: persecución
         * pura, sin predicción — su dificultad sale de no soltarte la Y.
         */
        CHASE,
        /** Extrapola la trayectoria en línea recta (niveles 5-6). */
        LINEAR,
        /** Calcula el punto de intercepción con rebotes en las paredes (7-9). */
        REFLECT
    }

    /**
     * Banda de 150 puntos de ELO por nivel: bajo 900 es nivel 1, el
     * jugador nuevo (1200) cae en nivel 4 y desde 1950 se juega contra el
     * nivel 9. Sin rating (reportes viejos o matchmaking sin el campo) se
     * asume el ELO inicial de la plataforma.
     */
    public static int levelForElo(Integer elo) {
        int rating = elo == null ? 1200 : elo;
        return Math.clamp(1L + Math.max(0, (rating - 750) / 150), 1, 9);
    }

    public static BotProfile forLevel(int level, BotProperties props) {
        int lvl = Math.clamp(level, 1, 9);
        PredictionTier tier;
        if (lvl <= 4) {
            tier = PredictionTier.CHASE;
        } else if (lvl <= 6) {
            tier = PredictionTier.LINEAR;
        } else {
            tier = PredictionTier.REFLECT;
        }
        return new BotProfile(
                lvl,
                Math.round(ramp(lvl, props.level1ReactionMillis(),
                        props.level4ReactionMillis(), props.level9ReactionMillis())),
                ramp(lvl, props.level1Speed(), props.level4Speed(), props.level9Speed()),
                ramp(lvl, props.level1AimErrorPx(),
                        props.level4AimErrorPx(), props.level9AimErrorPx()),
                tier,
                lvl >= 7);
    }

    /** Interpola en dos rampas ancladas en el nivel 4 (el bot clásico). */
    private static double ramp(int level, double at1, double at4, double at9) {
        return level <= 4
                ? lerp(at1, at4, (level - 1) / 3.0)
                : lerp(at4, at9, (level - 4) / 5.0);
    }

    private static double lerp(double from, double to, double t) {
        return from + (to - from) * t;
    }
}
