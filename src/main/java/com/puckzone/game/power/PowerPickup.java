package com.puckzone.game.power;

/**
 * Un poder puesto en el tablero, esperando a que una paleta lo recoja.
 * Entre su aparición y {@code activeFromEpochMs} está "telegrafiado": el
 * frontend lo pinta parpadeando y el servidor no permite recogerlo — ambos
 * jugadores tienen ese margen para reaccionar y disputarlo. Si nadie lo
 * toma antes de {@code expiresAtEpochMs}, desaparece.
 */
public record PowerPickup(
        PowerType type,
        double x,
        double y,
        long activeFromEpochMs,
        long expiresAtEpochMs
) {
}
