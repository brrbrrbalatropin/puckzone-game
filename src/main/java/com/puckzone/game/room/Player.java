package com.puckzone.game.room;

/**
 * Identidad de un jugador dentro de una sala, tal como la entrega
 * matchmaking. Inmutable: lo único que cambia durante la partida es el
 * {@link GameState}, no quién juega.
 */
public record Player(
        Long userId,
        String username,
        String university
) {
}
