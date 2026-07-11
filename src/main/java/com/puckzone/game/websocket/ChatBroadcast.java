package com.puckzone.game.websocket;

/**
 * Mensaje de chat ya validado, tal como se publica en
 * {@code /topic/lobby/chat} y como se guarda en el historial reciente.
 * username/university vienen del JWT del remitente (no son falsificables).
 */
public record ChatBroadcast(String userId, String username, String university,
                            String text, long sentAtEpochMs) {
}
