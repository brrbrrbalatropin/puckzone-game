package com.puckzone.game.room;

/**
 * Ciclo de vida de una partida.
 * WAITING:  sala creada por matchmaking; faltan jugadores por conectarse
 *           al WebSocket.
 * PLAYING:  todos conectados; el motor de física está corriendo.
 * FINISHED: un jugador alcanzó los goles de victoria.
 */
public enum GameStatus {
    WAITING,
    PLAYING,
    FINISHED
}
