package com.puckzone.game.room;

/**
 * Por qué terminó una partida. Con los finales por forfeit el marcador ya
 * no basta para saber quién ganó ni cómo: el frontend arma el overlay
 * final con esto y con {@code winnerId}.
 * SCORE:      un jugador llegó a los goles de victoria.
 * DISCONNECT: el jugador caído no volvió dentro de la ventana de gracia.
 * SURRENDER:  un jugador se rindió (confirmado desde el cliente).
 */
public enum FinishReason {
    SCORE,
    DISCONNECT,
    SURRENDER
}
