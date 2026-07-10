package com.puckzone.game.websocket;

/**
 * Payload de {@code /app/game/{gameId}/emote}: el jugador manda un emote al
 * rival. {@code emote} es un id de la lista blanca del servidor (THUMBS_UP,
 * LAUGH, WOW, CRY, ANGRY, GG) — nunca texto libre; el frontend lo traduce a
 * su emoji. Se retransmite por {@code /topic/game/{gameId}/emotes}. El
 * remitente sale del Principal de la sesión (JWT del handshake).
 */
public record EmoteMessage(String emote) {
}
