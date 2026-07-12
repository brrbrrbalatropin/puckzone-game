package com.puckzone.game.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Perillas del bot adaptativo (prefijo puckzone.game.bot). Se configuran
 * TRES anclas y los demás niveles se interpolan linealmente entre ellas
 * (rampas 1→4 y 4→9) en {@link com.puckzone.game.bot.BotProfile}:
 *
 * <ul>
 *   <li>Nivel 1: el más torpe (lento, miope y de reacción larga).</li>
 *   <li>Nivel 4: EL BOT CLÁSICO que solo sigue el disco (~220 px/s) — el
 *       que validaron los primeros jugadores; es el rival de la banda de
 *       ELO inicial (1200-1349), decisión del usuario 2026-07-12.</li>
 *   <li>Nivel 9: el techo (rápido y preciso, pero vencible).</li>
 * </ul>
 *
 * La filosofía: el nivel no cambia la estrategia sino las limitaciones
 * humanas — reacción, velocidad y puntería.
 *
 * @param defaultLevel nivel cuando no llega rating (1200 ⇒ 4).
 */
@ConfigurationProperties(prefix = "puckzone.game.bot")
public record BotProperties(
        int defaultLevel,
        double level1Speed,
        double level4Speed,
        double level9Speed,
        long level1ReactionMillis,
        long level4ReactionMillis,
        long level9ReactionMillis,
        double level1AimErrorPx,
        double level4AimErrorPx,
        double level9AimErrorPx
) {
}
