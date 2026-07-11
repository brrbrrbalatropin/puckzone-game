package com.puckzone.game.power;

/**
 * Un poder con duración ya activo en la partida. Los de área (OBSTACLE,
 * FAST_ZONE, SLOW_ZONE) quedan anclados donde estaba el pickup, con su
 * radio; SHIELD no usa posición (su efecto vive en el radio de la paleta
 * del dueño) pero figura aquí para que el frontend pinte su indicador.
 * Los instantáneos (GHOST_PUCK, CHAOS) no pasan por esta lista: son flags
 * del GameState.
 *
 * @param owner 1 o 2: quién lo recogió (para el HUD).
 */
public record ActiveEffect(
        PowerType type,
        int owner,
        double x,
        double y,
        double radius,
        long expiresAtEpochMs
) {
}
