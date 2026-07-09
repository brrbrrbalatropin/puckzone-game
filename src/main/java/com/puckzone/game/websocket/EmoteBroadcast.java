package com.puckzone.game.websocket;

/**
 * Lo que se publica en {@code /topic/game/{gameId}/emotes}: quién mandó el
 * emote (los clientes deciden en qué mitad de la cancha pintarlo) y cuál.
 */
public record EmoteBroadcast(String userId, String emote) {
}
