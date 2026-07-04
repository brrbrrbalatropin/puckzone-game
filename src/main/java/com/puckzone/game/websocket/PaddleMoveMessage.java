package com.puckzone.game.websocket;

/**
 * Payload de {@code /app/game/{gameId}/paddle}: coordenadas de mouse del
 * jugador. El servidor no las aplica tal cual: pasan por
 * {@code PhysicsEngine.movePaddle}, que las recorta a la mitad de cancha
 * del jugador.
 *
 * <p>TEMPORAL: {@code userId} declarado por el cliente hasta que el JWT
 * viva en el handshake (misma nota que {@link JoinMessage}).
 */
public record PaddleMoveMessage(String userId, double x, double y) {
}
