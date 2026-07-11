package com.puckzone.game.websocket;

/**
 * Lo único que el cliente envía a {@code /app/lobby/chat}: el texto. La
 * identidad (quién habla) sale del Principal de la sesión, nunca del
 * payload.
 */
public record ChatMessage(String text) {
}
