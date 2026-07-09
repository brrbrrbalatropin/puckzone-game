package com.puckzone.game.websocket;

/**
 * Payload de {@code /app/game/{gameId}/emote}: el jugador manda un emote al
 * rival. {@code emote} es un id de la lista blanca del servidor (THUMBS_UP,
 * LAUGH, WOW, CRY, ANGRY, GG) — nunca texto libre; el frontend lo traduce a
 * su emoji. Se retransmite por {@code /topic/game/{gameId}/emotes}.
 *
 * <p>TEMPORAL: el {@code userId} lo declara el cliente, misma nota que
 * {@link JoinMessage} (saldrá del handshake cuando el JWT viva ahí).
 */
public record EmoteMessage(String userId, String emote) {
}
