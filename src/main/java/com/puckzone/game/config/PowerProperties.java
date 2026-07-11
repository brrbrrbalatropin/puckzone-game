package com.puckzone.game.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Cadencias y tamaños del sistema de poderes, bajo
 * {@code puckzone.game.power}. Radios en píxeles del tablero 800x500.
 */
@ConfigurationProperties(prefix = "puckzone.game.power")
public record PowerProperties(
        // Cada cuánto aparece un pickup nuevo (contado desde que el
        // anterior se recogió o expiró).
        @DefaultValue("12") int spawnIntervalSeconds,
        // Fase de parpadeo: visible pero aún no recogible.
        @DefaultValue("2") int blinkSeconds,
        // Si nadie lo recoge en este tiempo (tras activarse), desaparece.
        @DefaultValue("10") int pickupLifetimeSeconds,
        // Duración de los efectos con duración (obstáculo, zonas, escudo).
        @DefaultValue("8") int effectDurationSeconds,
        @DefaultValue("18") int pickupRadius,
        @DefaultValue("35") int obstacleRadius,
        @DefaultValue("80") int zoneRadius
) {
}
