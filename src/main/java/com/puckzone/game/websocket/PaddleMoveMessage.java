package com.puckzone.game.websocket;

/**
 * Payload de {@code /app/game/{gameId}/paddle}: coordenadas de mouse del
 * jugador. El servidor no las aplica tal cual: pasan por
 * {@code PhysicsEngine.movePaddle}, que las recorta a la mitad de cancha
 * del jugador. La identidad sale del Principal de la sesión (JWT del
 * handshake), no del payload.
 */
public record PaddleMoveMessage(double x, double y) {
}
