package com.puckzone.game.websocket;

/**
 * Payload de {@code /app/game/{gameId}/join}: el jugador anuncia que ya
 * está conectado y suscrito al topic de la partida.
 *
 * <p>TEMPORAL: el {@code userId} lo declara el cliente. Cuando el JWT se
 * valide en el handshake, la identidad saldrá del Principal de la sesión
 * STOMP y este campo desaparecerá.
 */
public record JoinMessage(String userId) {
}
