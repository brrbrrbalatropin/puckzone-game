package com.puckzone.game.room;

/**
 * Ciclo de vida de una partida.
 * WAITING:  sala creada por matchmaking; faltan jugadores por conectarse
 *           al WebSocket.
 * PLAYING:  todos conectados; el motor de física está corriendo.
 * PAUSED:   un jugador se desconectó a mitad de partida; la física queda
 *           congelada mientras corre la ventana de gracia para reconectarse.
 * FINISHED: un jugador alcanzó los goles de victoria, o la partida terminó
 *           por abandono o rendición.
 */
public enum GameStatus {
    WAITING,
    PLAYING,
    PAUSED,
    FINISHED
}
